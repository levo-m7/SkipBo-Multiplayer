import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
    plugins: [
        sveltekit({
            compilerOptions: {
                // Can be removed in Svelte 6.
                runes: ({ filename }) =>
                    filename.split(/[/\\]/).includes('node_modules') ? undefined : true
            },

            adapter: adapter({
                fallback: 'index.html'
            })
        })
    ],

    server: {
        host: '0.0.0.0',
        allowedHosts: true,

        proxy: {
            '/api': {
                target: 'http://localhost:7070',
                changeOrigin: true
            },

            '/ws': {
                target: 'ws://localhost:7070',
                ws: true
            }
        }
    }
});
