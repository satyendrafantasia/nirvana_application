// Personal data never leaves the browser in an error report. These run on every event,
// breadcrumb and span before Sentry sends it.

const rules: [RegExp, string][] = [
  [/bearer\s+[\w.~+/=-]+/gi, 'Bearer [token]'],
  [/eyJ[\w-]+\.[\w-]+\.[\w-]*/g, '[token]'],
  [/[\w.%+-]+@[\w.-]+\.[a-z]{2,}/gi, '[email]'],
  [
    /\b(otp|code|token|access_token|refresh_token|password|secret|signature|key)(["']?\s*[=:]\s*["']?)[^\s&"',;}]+/gi,
    '$1$2[redacted]'
  ],
  [/(?<!\w)(?:\+?91[\s-]?)?[6-9]\d{4}[\s-]?\d{5}(?!\w)/g, '[phone]'],
  [/(?<![A-Za-z0-9])[\w-]{24,}(?![A-Za-z0-9])/g, '[token]'],
  [/(?<![\w.])\d{6}(?![\w.])/g, '[number]']
];

/** Masks emails, phone numbers, one-time codes and tokens in free text. */
export function scrubText(text: string): string;
export function scrubText(text: string | undefined): string | undefined;
export function scrubText(text: string | undefined) {
  if (typeof text !== 'string') return text;
  return rules.reduce((out, [pattern, replacement]) => out.replace(pattern, replacement), text);
}

// Path segments that carry a secret: private booking-reply links and email sign-in links.
const secretPaths: [RegExp, string][] = [
  [/\/b\/[^/]+/g, '/b/:token'],
  [/\/(?=\d+(\/|$))\d+/g, '/:id']
];

/**
 * A URL with no query string or fragment (search terms, sign-in codes and redirect targets live
 * there), numeric ids generalised and secrets masked. Keeps the origin so failing hosts are visible.
 */
export function scrubUrl(url: string): string;
export function scrubUrl(url: string | undefined): string | undefined;
export function scrubUrl(url: string | undefined) {
  if (typeof url !== 'string') return url;
  const bare = url.split(/[?#]/)[0];
  const match = /^([a-z][a-z0-9+.-]*:\/\/[^/]+)?(.*)$/i.exec(bare)!;
  const path = secretPaths.reduce((out, [pattern, replacement]) => out.replace(pattern, replacement), match[2]);
  return (match[1] || '') + scrubText(path);
}

type Bag = Record<string, unknown>;

/** Scrubs string values in a small key-value bag; anything nested is dropped. */
export function scrubData(data: Bag | undefined): Bag | undefined {
  if (!data) return data;
  const out: Bag = {};
  for (const [key, value] of Object.entries(data)) {
    if (/body|arguments|input|headers|cookie/i.test(key)) continue;
    if (typeof value === 'string') out[key] = /url|from|to|href/i.test(key) ? scrubUrl(value) : scrubText(value);
    else if (typeof value === 'number' || typeof value === 'boolean') out[key] = value;
  }
  return out;
}

type Crumb = { category?: string; message?: string; data?: Bag };

export function scrubBreadcrumb<T extends Crumb>(crumb: T): T | null {
  // Console output can contain anything a developer logged, including form state.
  if (crumb.category === 'console') return null;
  return { ...crumb, message: scrubText(crumb.message), data: scrubData(crumb.data) };
}

type Frame = { abs_path?: string; filename?: string };
type Exception = { value?: string; stacktrace?: { frames?: Frame[] } };
type Event = {
  message?: string;
  transaction?: string;
  user?: unknown;
  extra?: unknown;
  request?: { url?: string; data?: unknown; cookies?: unknown; headers?: Bag; query_string?: unknown };
  exception?: { values?: Exception[] };
  breadcrumbs?: Crumb[];
  tags?: Bag;
};

export function scrubEvent<T extends Event>(event: T): T {
  const out = { ...event };
  delete out.user;
  delete out.extra;
  if (out.request) {
    out.request = { url: scrubUrl(out.request.url) };
  }
  out.message = scrubText(out.message);
  out.transaction = scrubUrl(out.transaction);
  if (out.exception?.values)
    out.exception = {
      ...out.exception,
      values: out.exception.values.map((value) => ({
        ...value,
        value: scrubText(value.value),
        stacktrace: value.stacktrace && {
          ...value.stacktrace,
          frames: value.stacktrace.frames?.map((frame) => ({
            ...frame,
            abs_path: scrubUrl(frame.abs_path),
            filename: scrubUrl(frame.filename)
          }))
        }
      }))
    };
  if (out.breadcrumbs)
    out.breadcrumbs = out.breadcrumbs.map(scrubBreadcrumb).filter((crumb): crumb is Crumb => crumb !== null);
  if (out.tags) out.tags = scrubData(out.tags);
  return out;
}

type Span = { name: string; attributes: Bag };

export function scrubSpan<T extends Span>(span: T): T {
  return { ...span, name: scrubUrl(span.name), attributes: scrubData(span.attributes) ?? {} };
}
