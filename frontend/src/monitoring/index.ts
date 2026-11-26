// Browser error reporting. Off unless VITE_SENTRY_DSN is set when the app is built.
export type MonitoringConfig = {
  dsn: string;
  environment: string;
  release: string;
  tracesSampleRate: number;
};

/** Performance traces are sampled, never recorded for every visit. */
const MAX_TRACE_RATE = 0.2;
const DEFAULT_TRACE_RATE = 0.05;

export function monitoringConfig(env: Record<string, string | boolean | undefined>): MonitoringConfig | null {
  const dsn = typeof env.VITE_SENTRY_DSN === 'string' ? env.VITE_SENTRY_DSN.trim() : '';
  if (!/^https:\/\/[^@\s]+@[^/\s]+\/\d+$/.test(dsn)) return null;
  const rate = Number(env.VITE_SENTRY_TRACES_SAMPLE_RATE ?? DEFAULT_TRACE_RATE);
  return {
    dsn,
    environment: String(env.VITE_SENTRY_ENVIRONMENT || env.MODE || 'production'),
    release: String(env.VITE_SENTRY_RELEASE || __APP_RELEASE__),
    tracesSampleRate: Number.isFinite(rate) ? Math.min(Math.max(rate, 0), MAX_TRACE_RATE) : DEFAULT_TRACE_RATE
  };
}

type Client = typeof import('./sentry');
let client: Promise<Client> | null = null;

export function startMonitoring() {
  const config = monitoringConfig(import.meta.env);
  if (!config) return;
  client = import('./sentry').then((module) => {
    module.start(config);
    return module;
  });
  client.catch(() => {
    client = null;
  });
}

/** Reports an error the app caught itself, such as one stopped by the page error boundary. */
export function reportError(error: unknown, componentStack?: string) {
  void client?.then((module) => module.capture(error, componentStack)).catch(() => {});
}
