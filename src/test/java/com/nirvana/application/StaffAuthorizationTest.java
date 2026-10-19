package com.nirvana.application;
import com.nirvana.application.security.*;
import com.nirvana.application.repository.SpaRepository;
import com.nirvana.application.model.Spa;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@SpringBootTest @AutoConfigureMockMvc
class StaffAuthorizationTest {
 @Autowired MockMvc mvc;
 private UsernamePasswordAuthenticationToken auth(String role){
  var principal=new UserPrincipal(999999L,"access-test","test@example.test","",true,List.of(new SimpleGrantedAuthority(role)));
  return new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities());
 }
 @Test void customerCannotOpenStaffApis() throws Exception {
  for(String path:List.of("/api/admin/operations/dashboard","/api/admin/spas/1/bookings","/api/admin/analytics/spa/1","/api/admin/blackouts?spaId=1","/api/admin/audit?spaId=1","/api/onboarding/spas","/api/v1/corporates","/api/notifications/templates","/api/admin/support-overrides?spaId=1"))
   mvc.perform(get(path).with(authentication(auth("ROLE_USER")))).andExpect(status().isForbidden());
 }
 @Test void ownerCannotReadAnotherSpasBookings() throws Exception {
  mvc.perform(get("/api/admin/spas/1/bookings").with(authentication(auth("ROLE_SPA_OWNER")))).andExpect(status().isForbidden());
 }
 @Test void ownerCannotAccessPlatformAdministration() throws Exception {
  mvc.perform(get("/api/v1/corporates").with(authentication(auth("ROLE_SPA_OWNER")))).andExpect(status().isForbidden());
 }
 @Test void platformAdminCanReadBookingCalendar() throws Exception {
  mvc.perform(get("/api/admin/spas/1/bookings").with(authentication(auth("ROLE_PLATFORM_ADMIN")))).andExpect(status().isOk());
 }
 @Test void platformAdminCanReadCorporateSectionsWithoutExposingUserEntities() throws Exception {
  for(String path:List.of("/api/v1/corporates/1/deals","/api/v1/corporates/1/employees","/api/v1/corporates/1/onboarding/uploads")) {
   var response=mvc.perform(get(path).with(authentication(auth("ROLE_PLATFORM_ADMIN")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
   assertFalse(response.contains("password"));assertFalse(response.contains("hibernateLazyInitializer"));
  }
 }
 @Test void spaScopeAllowsOnlyAssignedOwner(){
  var repo=mock(SpaRepository.class);var access=new StaffAccess(repo);
  SecurityContextHolder.getContext().setAuthentication(auth("ROLE_SPA_OWNER"));
  when(repo.findByIdAndSpaManager_User_Id(10L,999999L)).thenReturn(Optional.of(new Spa()));
  try{assertTrue(access.spa(10L));assertFalse(access.spa(11L));assertFalse(access.admin());}finally{SecurityContextHolder.clearContext();}
 }
}
