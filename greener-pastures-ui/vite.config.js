import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In PROD the built bundle is loaded in-game by MCEF via `mod://greenerpastures/index.html`, so asset paths
// MUST be relative (base: './') and the output goes straight into the mod's resources. In DEV, `npm run dev`
// serves at localhost:5173 and the bridge SDK (src/bridge.js) connects to the live game over ws://127.0.0.1.
export default defineConfig({
  plugins: [react()],
  base: './',
  build: {
    outDir: '../greener-pastures/src/main/resources/assets/greenerpastures/html',
    emptyOutDir: true,
  },
  server: { port: 5173, strictPort: true },
})
