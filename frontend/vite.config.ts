import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
export default defineConfig({
 base: '/plugins/simnook/assets/frontend/',
 define: { __BUILD_VERSION__: JSON.stringify('0.3.0-beta'), __BUILD_GIT_HASH__: JSON.stringify('SIMnook') },
 plugins: [vue(), tailwindcss()],
 resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)), '@shared': fileURLToPath(new URL('./shared', import.meta.url)) } },
 server: {host: '127.0.0.1'},
 build: { cssCodeSplit: false, manifest: true }
})
