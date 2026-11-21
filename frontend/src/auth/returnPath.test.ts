import { describe, expect, it } from 'vitest';
import { returnPath } from './returnPath';

describe('sign-in return path', () => {
  it('preserves a listing claim including its query and fragment', () => {
    const path = '/owner/claim?placeId=abc#verify';
    expect(returnPath(path, '/owner', true)).toBe(path);
  });
  it('rejects external and invalid owner destinations', () => {
    for (const path of ['//example.com', '/\\example.com', 'https://example.com', '/admin']) {
      expect(returnPath(path, '/owner', true)).toBe('/owner');
    }
  });
});
