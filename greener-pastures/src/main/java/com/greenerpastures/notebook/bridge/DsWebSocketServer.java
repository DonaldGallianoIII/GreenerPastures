package com.greenerpastures.notebook.bridge;

import com.greenerpastures.core.GpLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * A tiny, dependency-free WebSocket server (RFC 6455, text frames) bound to <b>loopback only</b> — the transport
 * for the Notebook console's React UI (dev browser AND, later, MCEF in-game). Hand-rolled on a plain
 * {@link ServerSocket} so we nest <i>nothing</i> (no Netty HTTP codec): the traffic is small loopback JSON, so
 * this is more than enough and keeps the jar tiny.
 *
 * <p>Server→client frames are unmasked text; client→server frames are masked (we unmask). One reader thread per
 * connection; broadcasts fan out to all open connections. Never throws to callers — a bad socket just drops.
 */
public final class DsWebSocketServer {
    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final int port;
    private final String token;
    private final BiConsumer<DsWebSocketServer, String> onMessage;   // (server, textPayload) on the reader thread
    private final Runnable onConnect;                                // fired when a client completes the handshake

    private volatile ServerSocket serverSocket;
    private volatile boolean running;
    private final List<Conn> conns = new CopyOnWriteArrayList<>();

    public DsWebSocketServer(int port, String token, BiConsumer<DsWebSocketServer, String> onMessage, Runnable onConnect) {
        this.port = port;
        this.token = token;
        this.onMessage = onMessage;
        this.onConnect = onConnect;
    }

    /** Bind + start accepting on a daemon thread. Idempotent-ish; logs + no-ops on bind failure. */
    public synchronized void start() {
        if (running) return;
        try {
            serverSocket = new ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"));
            running = true;
            Thread accept = new Thread(this::acceptLoop, "GreenerPastures-DsBridge-accept");
            accept.setDaemon(true);
            accept.start();
            GpLog.i("bridge", "listen", "port", Integer.toString(port));
        } catch (IOException e) {
            GpLog.w("bridge", "bind_failed", "port", Integer.toString(port), "err", String.valueOf(e));
        }
    }

    public synchronized void stop() {
        running = false;
        for (Conn c : conns) c.close();
        conns.clear();
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) { }
        serverSocket = null;
    }

    public boolean hasClients() { return !conns.isEmpty(); }

    /** Broadcast a text message to every open connection. */
    public void broadcast(String text) {
        byte[] frame = encodeText(text);
        for (Conn c : conns) c.send(frame);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                Conn conn = new Conn(socket);
                Thread t = new Thread(conn::run, "GreenerPastures-DsBridge-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (running) GpLog.w("bridge", "accept_err", "err", String.valueOf(e));
            }
        }
    }

    // ── one connection ──────────────────────────────────────────────────────────────────────────────────
    private final class Conn {
        private final Socket socket;
        private volatile OutputStream out;
        private volatile boolean open;

        Conn(Socket socket) { this.socket = socket; }

        void run() {
            try {
                socket.setTcpNoDelay(true);
                InputStream in = socket.getInputStream();
                this.out = socket.getOutputStream();
                if (!handshake(in, out)) { close(); return; }
                open = true;
                conns.add(this);
                GpLog.i("bridge", "client_connect", "clients", Integer.toString(conns.size()));
                if (onConnect != null) { try { onConnect.run(); } catch (Throwable ignored) { } }
                readLoop(in);
            } catch (IOException ignored) {
                // client dropped
            } finally {
                close();
            }
        }

        /** Read the HTTP upgrade, validate the loopback token, reply 101. Returns false to reject. */
        private boolean handshake(InputStream in, OutputStream out) throws IOException {
            StringBuilder req = new StringBuilder();
            int b;
            while ((b = in.read()) != -1) {
                req.append((char) b);
                int len = req.length();
                if (len >= 4 && req.charAt(len - 4) == '\r' && req.charAt(len - 3) == '\n'
                        && req.charAt(len - 2) == '\r' && req.charAt(len - 1) == '\n') break;   // blank line = end of headers
                if (len > 8192) break;   // header flood guard
            }
            String headers = req.toString();
            String key = headerValue(headers, "sec-websocket-key");
            if (key == null) return false;
            // token gate: query (?ds_token=) OR header must match (loopback, but keep it honest)
            if (token != null && !token.isEmpty()) {
                String reqLine = headers.lines().findFirst().orElse("");
                boolean ok = reqLine.contains("ds_token=" + token) || token.equals(headerValue(headers, "ds-token"));
                if (!ok) { GpLog.w("bridge", "reject_token"); return false; }
            }
            String accept = Base64.getEncoder().encodeToString(sha1(key + WS_MAGIC));
            String resp = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
            out.write(resp.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        }

        private void readLoop(InputStream in) throws IOException {
            java.io.ByteArrayOutputStream msg = new java.io.ByteArrayOutputStream();
            while (open && running) {
                int b0 = in.read();
                if (b0 == -1) break;
                boolean fin = (b0 & 0x80) != 0;
                int opcode = b0 & 0x0F;
                int b1 = in.read();
                if (b1 == -1) break;
                boolean masked = (b1 & 0x80) != 0;
                long len = b1 & 0x7F;
                if (len == 126) len = (readN(in, 2));
                else if (len == 127) len = (readN(in, 8));
                byte[] mask = new byte[4];
                if (masked) readFully(in, mask, 4);
                byte[] payload = new byte[(int) len];
                readFully(in, payload, (int) len);
                if (masked) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];

                switch (opcode) {
                    case 0x8 -> { close(); return; }                 // close
                    case 0x9 -> send(encodeFrame(0xA, payload));      // ping → pong
                    case 0x0, 0x1 -> {                               // continuation / text
                        msg.write(payload);
                        if (fin) {
                            String text = msg.toString(StandardCharsets.UTF_8);
                            msg.reset();
                            if (onMessage != null) { try { onMessage.accept(DsWebSocketServer.this, text); } catch (Throwable ignored) { } }
                        }
                    }
                    default -> { /* ignore binary/pong */ }
                }
            }
        }

        void send(byte[] frame) {
            if (!open) return;
            try { synchronized (this) { out.write(frame); out.flush(); } }
            catch (IOException e) { close(); }
        }

        void close() {
            open = false;
            conns.remove(this);
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    // ── frame + hash helpers ──────────────────────────────────────────────────────────────────────────────
    private static byte[] encodeText(String text) { return encodeFrame(0x1, text.getBytes(StandardCharsets.UTF_8)); }

    private static byte[] encodeFrame(int opcode, byte[] payload) {
        int len = payload.length;
        int header = len < 126 ? 2 : len < 65536 ? 4 : 10;
        byte[] frame = new byte[header + len];
        frame[0] = (byte) (0x80 | opcode);   // FIN + opcode
        if (len < 126) {
            frame[1] = (byte) len;
        } else if (len < 65536) {
            frame[1] = 126;
            frame[2] = (byte) (len >> 8);
            frame[3] = (byte) len;
        } else {
            frame[1] = 127;
            for (int i = 0; i < 8; i++) frame[2 + i] = (byte) (((long) len) >> (56 - 8 * i));
        }
        System.arraycopy(payload, 0, frame, header, len);
        return frame;
    }

    private static long readN(InputStream in, int n) throws IOException {
        long v = 0;
        for (int i = 0; i < n; i++) { int b = in.read(); if (b == -1) throw new IOException("eof"); v = (v << 8) | (b & 0xFF); }
        return v;
    }

    private static void readFully(InputStream in, byte[] buf, int n) throws IOException {
        int off = 0;
        while (off < n) { int r = in.read(buf, off, n - off); if (r == -1) throw new IOException("eof"); off += r; }
    }

    private static String headerValue(String headers, String nameLower) {
        for (String line : headers.split("\r\n")) {
            int i = line.indexOf(':');
            if (i > 0 && line.substring(0, i).trim().toLowerCase(java.util.Locale.ROOT).equals(nameLower)) {
                return line.substring(i + 1).trim();
            }
        }
        return null;
    }

    private static byte[] sha1(String s) {
        try { return MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
