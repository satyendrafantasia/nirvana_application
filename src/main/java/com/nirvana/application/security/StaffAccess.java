package com.nirvana.application.security;
import com.nirvana.application.repository.SpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
@Component("staffAccess") @RequiredArgsConstructor
public class StaffAccess {
 /** A spa's front-desk staff: added by the owner, with day-to-day desk rights only. */
 public static final String DESK_ROLE="ROLE_SPA_STAFF";
 private final SpaRepository spas;
 private final JdbcTemplate jdbc;
 public boolean admin(){return has("ROLE_ADMIN","ROLE_PLATFORM_ADMIN");}
 public boolean staff(){return admin() || has("ROLE_SPA_OWNER","ROLE_SPA_MANAGER","ROLE_Spa_MANAGER");}
 private boolean has(String... roles){
  var auth=SecurityContextHolder.getContext().getAuthentication();
  return auth!=null && auth.isAuthenticated() && auth.getAuthorities().stream().anyMatch(a->java.util.Arrays.asList(roles).contains(a.getAuthority()));
 }
 /** The spa's owner or manager (or a platform admin): full access, money and settings included. */
 public boolean spa(Long id){
  if(admin())return true;
  if(!staff() || id==null)return false;
  return spas.existsByIdAndSpaManager_User_Id(id,SecurityUtils.getCurrentUserId());
 }
 /** Anyone who runs this spa's front desk: the owner, or staff the owner added to this spa. */
 public boolean desk(Long id){
  if(spa(id))return true;
  if(id==null || !has(DESK_ROLE))return false;
  Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM spa_desk_staff WHERE spa_id=? AND user_id=?",Integer.class,id,SecurityUtils.getCurrentUserId());
  return n!=null && n>0;
 }
}
