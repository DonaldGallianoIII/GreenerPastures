package com.greenerpastures.studio;

import com.greenerpastures.client.ui.DaemonController;
import com.greenerpastures.client.ui.DaemonView;
import com.greenerpastures.client.ui.NotebookView;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Design Studio — INTERACTIVE mode. A desktop window that runs the real {@link DaemonController}
 * (the same state + input code as in-game), so you can drag nodes, wire pairs, right-click to unpair,
 * scroll to zoom — exactly like the Minecraft screen, but without launching Minecraft.
 *
 * <p>Run: {@code ./gradlew studioLive}. Needs a desktop display (WSLg on Win11, or an X server).
 */
public final class StudioLive {
    private StudioLive() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(StudioLive::open);
    }

    private static void open() {
        DaemonController ctrl = StudioData.demoController();
        JFrame frame = new JFrame("Greener Pastures — Daemon (Design Studio · live)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        DaemonPanel panel = new DaemonPanel(ctrl);
        frame.setContentPane(panel);
        frame.setSize(960, 600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        new Timer(50, e -> { ctrl.tickFlash(); panel.repaint(); }).start();   // animate flash messages
    }

    private static final class DaemonPanel extends JPanel {
        private final DaemonController ctrl;

        DaemonPanel(DaemonController ctrl) {
            this.ctrl = ctrl;
            setBackground(Color.BLACK);
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e)  { ctrl.mouseDown(e.getX(), e.getY(), btn(e)); repaint(); }
                @Override public void mouseDragged(MouseEvent e)  { ctrl.mouseDrag(e.getX(), e.getY()); repaint(); }
                @Override public void mouseReleased(MouseEvent e) { ctrl.mouseUp(e.getX(), e.getY(), btn(e)); repaint(); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(e -> { ctrl.scroll(e.getX(), e.getY(), -e.getWheelRotation()); repaint(); });
        }

        private static int btn(MouseEvent e) {
            return e.getButton() == MouseEvent.BUTTON3 ? 1 : e.getButton() == MouseEvent.BUTTON2 ? 2 : 0;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            ctrl.setViewport(getWidth(), getHeight(), NotebookView.CHROME_TOP);
            DaemonView.Model m = ctrl.buildModel();
            NotebookView.paint(new Java2DGpCanvas((Graphics2D) g), m, StudioData.frame(), getWidth(), getHeight());
        }
    }
}
