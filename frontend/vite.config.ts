import { defineConfig, loadEnv, type Plugin } from 'vite';
import { robots, sitemap, seoHead, publicOrigin } from './src/seo/site';
import react from '@vitejs/plugin-react';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { isAppRoute } from './src/seo/routes';
const home =
  '<main><h1>Book spa and wellness services with Nirvana</h1><p>Search by city, spa name or treatment, compare prices and select an available appointment.</p><a href="/spas">Book an appointment</a></main>';
function publicPages(origin: string): Plugin {
  const files: Record<string, string> = {
    'robots.txt': robots(origin),
    'sitemap.xml': sitemap(origin)
  };
  return {
    name: 'nirvana-public-pages',
    transformIndexHtml(html, ctx) {
      const isHome =
        (ctx.originalUrl || ctx.path).split('?')[0] === '/' ||
        (ctx.path === '/index.html' && !ctx.server);
      return html
        .replace('<!--site-head-->', seoHead(isHome, origin))
        .replace('<!--site-content-->', isHome ? home : '');
    },
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const path = req.url?.split('?')[0].slice(1) || '';
        if (!Object.hasOwn(files, path)) return next();
        res.setHeader('Content-Type', path.endsWith('xml') ? 'application/xml' : 'text/plain');
        res.end(files[path]);
      });
    },
    configurePreviewServer(server) {
      const out = resolve(server.config.root, server.config.build.outDir);
      server.middlewares.use(async (req, res, next) => {
        const path = req.url?.split('?')[0] || '/';
        if (
          !['GET', 'HEAD'].includes(req.method || '') ||
          path === '/' ||
          [
            '/index.html',
            '/robots.txt',
            '/sitemap.xml',
            '/nirvana.svg',
            '/site.webmanifest',
            '/apple-touch-icon.png',
            '/nirvana-social.png'
          ].includes(path) ||
          path.startsWith('/assets/') ||
          path.startsWith('/api/') ||
          !req.headers.accept?.includes('text/html')
        )
          return next();
        const found = isAppRoute(path);
        try {
          const content = await readFile(resolve(out, found ? 'app.html' : '404.html'));
          res.statusCode = found ? 200 : 404;
          res.setHeader('Content-Type', 'text/html; charset=utf-8');
          res.end(req.method === 'HEAD' ? undefined : content);
        } catch (error) {
          next(error);
        }
      });
    },
    generateBundle: {
      order: 'post',
      handler(_options, bundle) {
        for (const [fileName, source] of Object.entries(files))
          this.emitFile({ type: 'asset', fileName, source });
        const index = bundle['index.html'];
        if (index?.type !== 'asset') this.error('Missing SPA entry point');
        const shell = String(index.source)
          .replace(seoHead(true, origin), seoHead(false, origin))
          .replace(home, '');
        this.emitFile({ type: 'asset', fileName: 'app.html', source: shell });
        this.emitFile({
          type: 'asset',
          fileName: '404.html',
          source: shell.replace(
            '<div id="root"></div>',
            '<div id="root"><h1>Page not found</h1><a href="/">Return home</a> · <a href="/spas">Explore services</a></div>'
          )
        });
      }
    }
  };
}
export default defineConfig(({ command, mode }) => ({
  plugins: [
    react(),
    publicPages(
      publicOrigin(
        command === 'build' && mode === 'production'
          ? loadEnv(mode, '.').VITE_PUBLIC_SITE_URL
          : undefined
      )
    )
  ],
  server: {
    port: 5173,
    proxy: Object.fromEntries(
      ['/api', '/oauth2', '/login/oauth2'].map((path) => [
        path,
        { target: process.env.API_PROXY_TARGET || 'http://localhost:8081', changeOrigin: false }
      ])
    )
  }
}));
