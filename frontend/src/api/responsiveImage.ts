/** Hosts that resize a photo to the width given in its `w` parameter (and send AVIF/WebP when the browser takes it). */
const RESIZABLE = ['images.pexels.com', 'images.unsplash.com'];
const WIDTHS = [400, 600, 800, 1200];

/**
 * `src` and `srcSet` for a photo, so each screen downloads a size it can show: a spa card about
 * 400px wide gets a 400-800px photo instead of a 1000px one. Other hosts' photos are left as they are.
 */
export function responsiveImage(url: string, fallbackWidth = 800): { src: string; srcSet?: string } {
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return { src: url };
  }
  if (!RESIZABLE.includes(parsed.hostname)) return { src: url };
  const at = (w: number) => {
    const u = new URL(parsed);
    u.searchParams.set('w', String(w));
    return u.toString();
  };
  return { src: at(fallbackWidth), srcSet: WIDTHS.map((w) => `${at(w)} ${w}w`).join(', ') };
}
