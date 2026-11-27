import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom';
import { AuthContext } from '../../contexts/AuthContext';
import FeatureMenu, { ACCOUNT_MENU, ADMIN_MENU } from './FeatureMenu';
import { features } from '../../features/model';

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
    expect(ACCOUNT_MENU).toHaveLength(7);
    expect(html).toContain('href="/gift-cards"');
    for (const item of ACCOUNT_MENU) expect(html).toContain(`href="${item.to}"`);
    for (const gone of ['map', 'nearby', 'google-booking', 'availability', 'package-history', 'spa-packages', 'benefits'])
      expect(html).not.toContain(`/features/${gone}"`);
    expect(html).not.toContain('/auth/password');
    expect(html).not.toContain('Manage spa');
  });
  it('leaves spa management to the owner dashboard', () => {
    const owner = render(['ROLE_USER', 'ROLE_SPA_OWNER']);
    expect(owner).not.toContain('>Admin<');
    expect(owner).not.toContain('/admin/listing-claims');
  });
  it('gives admins one grouped menu', () => {
    const admin = render(['ROLE_ADMIN']);
    expect(admin).toContain('>Admin<');
    expect(admin).not.toContain('Manage spa');
    for (const { group, items } of ADMIN_MENU) {
      expect(admin).toContain(`>${group}<`);
      for (const item of items) expect(admin).toContain(`href="${item.to}"`);
    }
  });
  it('still reaches every admin and spa tool', () => {
    const linked = ADMIN_MENU.flatMap((g) => g.items.map((i) => i.to));
    for (const f of features.filter((f) => f.access !== 'customer' && f.id !== 'onboarding-drafts'))
      expect(linked).toContain(`/features/${f.id}`);
    for (const page of ['/admin/listing-claims', '/admin/city-discovery', '/staff/media'])
      expect(linked).toContain(page);
  });
});
