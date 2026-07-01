import React from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.jsx'
import { connect } from './bridge.js'

// Open the WS to Minecraft (prod: params from the mod:// URL; dev: localhost:25599; no game: mock data).
connect()

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
