import { describe, expect, it } from 'vitest';
import { passwordProblem } from './accountApi';

describe('password change checks', () => {
  it('accepts a valid change', () => {
    expect(passwordProblem('old-secret', 'new-secret-1', 'new-secret-1')).toBeUndefined();
  });
  it('asks for the current password', () => {
    expect(passwordProblem('', 'new-secret-1', 'new-secret-1')).toMatch(/current password/);
  });
  it('rejects short, unchanged and mismatched passwords', () => {
    expect(passwordProblem('old-secret', 'short', 'short')).toMatch(/at least 8/);
    expect(passwordProblem('same-secret', 'same-secret', 'same-secret')).toMatch(/different/);
    expect(passwordProblem('old-secret', 'new-secret-1', 'new-secret-2')).toMatch(/do not match/);
  });
  it('rejects passwords longer than 72 bytes, counting multi-byte characters', () => {
    expect(passwordProblem('old', 'a'.repeat(72), 'a'.repeat(72))).toBeUndefined();
    expect(passwordProblem('old', 'a'.repeat(73), 'a'.repeat(73))).toMatch(/too long/);
    const wide = 'é'.repeat(37); // 74 bytes in UTF-8
    expect(passwordProblem('old', wide, wide)).toMatch(/too long/);
  });
});
