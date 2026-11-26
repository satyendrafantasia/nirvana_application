/// <reference types="vite/client" />

/** "nirvana-frontend@<package version>", set in vite.config.ts; VITE_SENTRY_RELEASE overrides it. */
declare const __APP_RELEASE__: string;

interface ImportMetaEnv {
  readonly VITE_SENTRY_DSN?: string;
  readonly VITE_SENTRY_ENVIRONMENT?: string;
  readonly VITE_SENTRY_RELEASE?: string;
  readonly VITE_SENTRY_TRACES_SAMPLE_RATE?: string;
}
