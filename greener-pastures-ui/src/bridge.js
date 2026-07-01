/*
 * Data Science Runtime — client bridge SDK.
 *
 * ONE transport for BOTH dev (browser) and prod (MCEF in-game): a WebSocket to the running game.
 *   - PROD: MCEF loads `mod://greenerpastures/index.html?ds_port=<p>&ds_token=<t>` → we read those params.
 *   - DEV:  `npm run dev` in a browser → no params → fall back to VITE_DS_PORT (default 25599) + dev token.
 *   - NO GAME: MOCK MODE — serve the contract's sample data so the UI renders standalone; auto-upgrades to
 *     live the moment a real game socket answers.
 *
 * Contract: NOTEBOOK_DATA_CONTRACT.md.
 *   server→client  { "type":"state",  "channel":"<name>", "data": {…} }
 *   client→server  { "type":"action", "channel":"<name>", "action":"<ACTION>", "payload": {…} }
 */
import { useSyncExternalStore } from 'react'
import { MOCK } from './mockData.js'
import { applyMock } from './mockReducer.js'

const params = new URLSearchParams(typeof window !== 'undefined' ? window.location.search : '')
const PORT = params.get('ds_port') || import.meta.env?.VITE_DS_PORT || '25599'
const TOKEN = params.get('ds_token') || import.meta.env?.VITE_DS_TOKEN || 'dev'

const state = {}            // channel -> latest data snapshot
const listeners = new Set()
let socket = null
let mockMode = false
let hadOpen = false
let reconnectT = null

function emit() { listeners.forEach((l) => l()) }
function setChannel(channel, data) { state[channel] = data; emit() }

export function connect() {
  if (typeof WebSocket === 'undefined') return enterMock()
  let s
  try {
    s = new WebSocket(`ws://127.0.0.1:${PORT}?ds_token=${encodeURIComponent(TOKEN)}`)
  } catch {
    return enterMock()
  }
  socket = s
  s.onopen = () => { hadOpen = true; mockMode = false }
  s.onmessage = (ev) => {
    let msg
    try { msg = JSON.parse(ev.data) } catch { return }
    if (msg.type === 'state' && msg.channel) setChannel(msg.channel, msg.data)
  }
  s.onerror = () => { try { s.close() } catch { /* noop */ } }
  s.onclose = () => {
    socket = null
    if (!hadOpen) enterMock()   // never connected → show mock meanwhile
    scheduleReconnect()         // keep trying; upgrades to live when the game answers
  }
}

function scheduleReconnect() {
  clearTimeout(reconnectT)
  reconnectT = setTimeout(connect, 2000)
}

function enterMock() {
  if (mockMode) return
  mockMode = true
  for (const [ch, data] of Object.entries(MOCK)) setChannel(ch, data)
  console.info('[ds-bridge] no game socket — MOCK MODE (contract sample data). Actions are logged, not sent.')
}

/** Send an action. LIVE → the game; MOCK → applied to local state so the UI updates instantly. */
export function send(channel, action, payload = {}) {
  if (mockMode) {
    const updates = applyMock(state, channel, action, payload)
    for (const [ch, data] of Object.entries(updates)) setChannel(ch, data)
    return
  }
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    console.info('[ds-bridge][offline] action (no game socket)', { channel, action, payload })
    return
  }
  socket.send(JSON.stringify({ type: 'action', channel, action, payload }))
}

export function isMock() { return mockMode }

/** React hook: subscribe to a channel's latest state; re-renders on every push. Returns undefined until first frame. */
export function useChannel(channel) {
  return useSyncExternalStore(
    (cb) => { listeners.add(cb); return () => listeners.delete(cb) },
    () => state[channel],
    () => state[channel],
  )
}
