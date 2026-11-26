// Loaded only when VITE_SENTRY_DSN is set at build time. Without it the browser never downloads
// this chunk and nothing is sent.
import * as Sentry from '@sentry/react';
import { scrubBreadcrumb, scrubEvent, scrubSpan } from './scrub';
import type { MonitoringConfig } from './index';

export function start(config: MonitoringConfig) {
  Sentry.init({
    dsn: config.dsn,
    environment: config.environment,
    release: config.release,
    // The v11 replacement for sendDefaultPii: false. No user details, cookies, headers,
    // query strings or request/response bodies are collected.
    dataCollection: {
      userInfo: false,
      cookies: false,
      httpHeaders: false,
      httpBodies: [],
      urlQueryParams: false
    },
    integrations: config.tracesSampleRate > 0 ? [Sentry.browserTracingIntegration()] : [],
    tracesSampleRate: config.tracesSampleRate,
    // Trace headers go only to our own API, never to Razorpay, Google or other third parties.
    tracePropagationTargets: [/^\/api\//, new RegExp('^' + escape(window.location.origin) + '/api/')],
    beforeBreadcrumb: (breadcrumb) => scrubBreadcrumb(breadcrumb),
    beforeSend: (event) => scrubEvent(event),
    beforeSendSpan: (span) => scrubSpan(span)
  });
}

export function capture(error: unknown, componentStack?: string) {
  Sentry.withScope((scope) => {
    if (componentStack) scope.setContext('react', { componentStack });
    Sentry.captureException(error);
  });
}

function escape(text: string) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
