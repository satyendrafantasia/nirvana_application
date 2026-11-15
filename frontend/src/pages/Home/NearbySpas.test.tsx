import React from 'react';
import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import NearbySpas, { locationProblem } from './NearbySpas';

const render = (node: React.ReactNode) =>
  renderToStaticMarkup(<StaticRouter location="/">{node}</StaticRouter>);

describe('nearby spas on the home page', () => {
  it('asks signed-out visitors to sign in and does not offer the location button', () => {
    const html = render(<NearbySpas signedIn={false} />);
    expect(html).toContain('Spas near you');
    expect(html).toContain('/auth/login?redirect=/');
    expect(html).not.toContain('Show spas near me');
  });
  it('offers the location button with a privacy note, without loading anything on render', () => {
    const html = render(<NearbySpas signedIn />);
    expect(html).toContain('Show spas near me');
    expect(html).toContain('is not saved');
    expect(html).not.toContain('Sign in');
  });
  it('explains each kind of location failure', () => {
    expect(locationProblem(1)).toMatch(/permission is blocked/);
    expect(locationProblem(3)).toMatch(/too long/);
    expect(locationProblem(2)).toMatch(/unavailable/);
  });
});
