import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthContext } from '../../contexts/AuthContext';
import OwnerPortal from './OwnerPortal';
import Register from '../Auth/Register';
import Login from '../Auth/Login';
function render(element: React.ReactNode, roles: string[] = ['ROLE_USER']) {
  const auth = {
    user: { id: '1', firstName: 'Test', lastName: 'User', email: 'test@example.test', roles },
    token: 'test',
    isLoading: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    hydrateUser: vi.fn(),
    setUser: vi.fn(),
    acceptAuth: vi.fn()
  };
  return renderToStaticMarkup(
    <QueryClientProvider client={new QueryClient()}>
      <AuthContext.Provider value={auth}>
        <StaticRouter location="/owner">{element}</StaticRouter>
      </AuthContext.Provider>
    </QueryClientProvider>
  );
}
describe('Owner entry and access boundaries', () => {
  it('offers verified owner registration without an administrator selector', () => {
    const html = render(<Register owner />);
    expect(html).toContain('Create your spa owner account');
    expect(html).toContain('/owner/login');
    expect(html).toContain('Send verification code');
    expect(html).not.toContain('value="ADMIN"');
  });
  it('links owner login to dedicated signup', () => {
    const html = render(<Login owner />);
    expect(html).toContain('Spa owner sign in');
    expect(html).toContain('/owner/signup');
  });
  it('blocks a customer from owner data screens', () => {
    const html = render(<OwnerPortal />);
    expect(html).toContain('Owner access required');
    expect(html).not.toContain('Owner workspace');
  });
  it('exposes scoped management tabs to an owner', () => {
    const html = render(<OwnerPortal />, ['ROLE_USER', 'ROLE_SPA_OWNER']);
    for (const section of [
      'bookings',
      'availability',
      'therapists',
      'services',
      'packages',
      'settings',
      'onboarding'
    ])
      expect(html).toContain('/owner/' + section);
    expect(html).not.toContain('/features/files');
  });
});
