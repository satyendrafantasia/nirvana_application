import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import { AuthContext } from '../../contexts/AuthContext';
import FeatureMenu, { ACCOUNT_MENU } from './FeatureMenu';

function render(roles: string[]) {
  const auth = {
    user: { id: '1', firstName: 'T', lastName: 'U', email: 't@example.test', roles },
    token: 't',
    isLoading: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    hydrateUser: vi.fn(),
    setUser: vi.fn(),
    acceptAuth: vi.fn()
  };
  return renderToStaticMarkup(
    <AuthContext.Provider value={auth}>
      <StaticRouter location="/">
        <FeatureMenu />
      </StaticRouter>
    </AuthContext.Provider>
  );
}

describe('account menu', () => {
  it('keeps to what a guest manages about themselves', () => {
    const html = render(['ROLE_USER']);
    expect(ACCOUNT_MENU).toHaveLength(6);
    for (const item of ACCOUNT_MENU) expect(html).toContain(`href="${item.to}"`);
    for (const gone of ['map', 'nearby', 'google-booking', 'availability', 'package-history', 'spa-packages', 'benefits'])
      expect(html).not.toContain(`/features/${gone}"`);
    expect(html).not.toContain('/auth/password');
    expect(html).not.toContain('Manage spa');
  });
  it('still shows the spa and admin menus to staff', () => {
    expect(render(['ROLE_USER', 'ROLE_SPA_OWNER'])).toContain('Manage spa');
    expect(render(['ROLE_ADMIN'])).toContain('Administration');
  });
});
