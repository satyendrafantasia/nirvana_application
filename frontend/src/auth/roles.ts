export const isAdmin = (roles: string[] = []) =>
  roles.some((r) => ['ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN'].includes(r));
/** Runs a spa: an owner or manager, as opposed to a platform admin. */
export const ownsSpas = (roles: string[] = []) =>
  roles.some((r) => ['ROLE_SPA_OWNER', 'ROLE_SPA_MANAGER', 'ROLE_Spa_MANAGER'].includes(r));
export const isStaff = (roles: string[] = []) => isAdmin(roles) || ownsSpas(roles);
export const canOpen = (access: string, roles: string[] = []) =>
  access === 'admin' ? isAdmin(roles) : access === 'staff' ? isStaff(roles) : true;
