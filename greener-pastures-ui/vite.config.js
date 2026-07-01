import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { viteSingleFile } from 'vite-plugin-singlefile'

// In PROD the app is bundled into a SINGLE self-contained index.html (viteSingleFile) — JS + CSS inlined, no
// external requests — which the mod extracts to a temp file and loads in-game over file:// (MCEF's mod:// scheme
// is broken on Fabric: its handler does getClassLoader().getResourceAsStream with a leading slash → always null).
// Single-file also sidesteps file://'s ES-module restrictions. In DEV, `npm run dev` serves at :5173 and the
// bridge SDK (src/bridge.js) connects to the live game over ws://127.0.0.1.
export default defineConfig({
  plugins: [react(), viteSingleFile()],
  base: './',
  build: {
    outDir: '../greener-pastures/src/main/resources/assets/greenerpastures/html',
    emptyOutDir: true,
  },
  server: { port: 5173, strictPort: true },
})
