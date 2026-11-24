import { describe, expect, it } from 'vitest';
import { responsiveImage } from './responsiveImage';

describe('photos sized for the screen', () => {
  it('offers resizable photos in several widths', () => {
    const { src, srcSet } = responsiveImage(
      'https://images.pexels.com/photos/1/p.jpeg?auto=compress&cs=tinysrgb&w=1000'
    );
    expect(src).toBe('https://images.pexels.com/photos/1/p.jpeg?auto=compress&cs=tinysrgb&w=800');
    expect(srcSet).toContain('w=400 400w');
    expect(srcSet).toContain('w=1200 1200w');
    expect(srcSet).toContain('auto=compress');
  });
  it('leaves other photos as they are', () => {
    expect(responsiveImage('/api/spas/1/google-photos/x/image')).toEqual({
      src: '/api/spas/1/google-photos/x/image'
    });
    expect(responsiveImage('https://example.com/a.jpg')).toEqual({ src: 'https://example.com/a.jpg' });
  });
});
