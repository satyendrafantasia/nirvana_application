package com.nirvana.application.security;
import com.nirvana.application.repository.SpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
@Component("staffAccess") @RequiredArgsConstructor
public class StaffAccess {
 private final SpaRepository spas;
 public boolean admin(){return has("ROLE_ADMIN","ROLE_PLATFORM_ADMIN");}
 public boolean staff(){return admin() || has("ROLE_SPA_OWNER","ROLE_SPA_MANAGER","ROLE_Spa_MANAGER");}
 private boolean has(String... roles){
  var auth=SecurityContextHolder.getContext().getAuthentication();
  return auth!=null && auth.isAuthenticated() && auth.getAuthorities().stream().anyMatch(a->java.util.Arrays.asList(roles).contains(a.getAuthority()));
 }
 public boolean spa(Long id){
  if(admin())return true;
  if(!staff() || id==null)return false;
  return spas.findByIdAndSpaManager_User_Id(id,SecurityUtils.getCurrentUserId()).isPresent();
 }
}
