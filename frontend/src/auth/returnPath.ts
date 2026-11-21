export function returnPath(requested: string | null, fallback: string, owner = false) {
  if (!requested || !requested.startsWith('/') || requested.startsWith('//') ||
      /[\\\u0000-\u001f]/.test(requested)) return fallback;
  if (owner && requested !== '/owner' && !requested.startsWith('/owner/')) return fallback;
  return requested;
}
