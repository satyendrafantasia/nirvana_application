export const isAdmin = (roles: string[] = []) =>
  roles.some((r) => ['ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN'].includes(r));
export const isStaff = (roles: string[] = []) =>
  isAdmin(roles) ||
  roles.some((r) => ['ROLE_SPA_OWNER', 'ROLE_SPA_MANAGER', 'ROLE_Spa_MANAGER'].includes(r));
export const canOpen = (access: string, roles: string[] = []) =>
  access === 'admin' ? isAdmin(roles) : access === 'staff' ? isStaff(roles) : true;
