import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthContext } from '../../contexts/AuthContext';
import CityDiscovery from './CityDiscovery';

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
describe('City discovery access', () => {
  it('blocks customers and spa owners', () => {
    for (const roles of [['ROLE_USER'], ['ROLE_SPA_OWNER']]) {
      const html = render(<CityDiscovery />, roles);
      expect(html).toContain('Only platform administrators');
      expect(html).not.toContain('Search and save references');
    }
  });
  it('shows discovery controls and storage limitations to platform admins', () => {
    const html = render(<CityDiscovery />, ['ROLE_PLATFORM_ADMIN']);
    expect(html).toContain('Search and save references');
    expect(html).toContain('Only Place IDs');
    expect(html).toContain('not published or bookable');
  });
});
