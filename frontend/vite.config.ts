import { defineConfig, loadEnv, type Plugin } from 'vite';
import { staticPagePaths, pageMeta } from './src/seo/pages';
import { pageHead, robots, sitemap, seoHead, publicOrigin } from './src/seo/site';
import react from '@vitejs/plugin-react';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { isAppRoute } from './src/seo/routes';
import { version } from './package.json';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server.js';
import HomeHero from './src/pages/Home/HomeHero';

// Deliver the same above-the-fold content before the browser starts React.
const hero = renderToStaticMarkup(createElement(StaticRouter, { location: '/' }, createElement(HomeHero)));
const home = '<header class="site-header"><div class="nav-shell mx-auto max-w-6xl px-4 py-3"><a class="brand" href="/">Nirvana Wellness</a></div></header>' +
  '<main class="home-shell mx-auto max-w-6xl px-4 py-10">' + hero + '</main>';
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
            '/nirvana-social.png',
            '/hero-spa.jpg'
          ].includes(path) ||
          path.startsWith('/assets/') ||
          path.startsWith('/api/') ||
          !req.headers.accept?.includes('text/html')
        )
          return next();
        const found = isAppRoute(path);
        try {
          const normalized = path.replace(/\/+$/, '');
          const file = staticPagePaths.includes(normalized) ? normalized.slice(1) + '/index.html' : found ? 'app.html' : '404.html';
          const content = await readFile(resolve(out, file));
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
        for (const path of staticPagePaths) {
          const body = '<main><h1>' + pageMeta[path].title + '</h1><p>' + pageMeta[path].summary + '</p><a href="/spas">Explore spas</a></main>';
          this.emitFile({ type: 'asset', fileName: path.slice(1) + '/index.html', source: shell
            .replace(seoHead(false, origin), pageHead(path, origin))
            .replace('<div id="root"></div>', '<div id="root">' + body + '</div>') });
        }
        this.emitFile({
          type: 'asset',
          fileName: '404.html',
          source: shell.replace(seoHead(false, origin), pageHead('/404', origin)).replace(
            '<div id="root"></div>',
            '<div id="root"><main class="error-page"><section class="error-card"><p>Nirvana Wellness · 404</p><h1>Page not found</h1><p>This link may have moved.</p><p><a href="/">Go home</a> · <a href="/spas">Explore services</a></p></section></main></div>'
          )
        });
      }
    }
  };
}
export default defineConfig(({ command, mode }) => ({
  define: { __APP_RELEASE__: JSON.stringify('nirvana-frontend@' + version) },
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
