package com.nirvana.application;
import com.nirvana.application.otp.*;
import com.nirvana.application.security.*;
import com.nirvana.application.repository.*;
import com.nirvana.application.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import java.time.OffsetDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@SpringBootTest @AutoConfigureMockMvc @Transactional
class OwnerJourneyTest {
 @Autowired MockMvc mvc; @Autowired OtpVerificationRepository otps;
 @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
 @Autowired SpaRepository spas;
 private UsernamePasswordAuthenticationToken auth(long id,String role){
  var p=new UserPrincipal(id,"owner-test","owner-test@example.test","",true,List.of(new SimpleGrantedAuthority(role)));
  return new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities());
 }
 private String verified(){
  String token=UUID.randomUUID().toString();
  var v=new OtpVerification();v.setVerificationId(UUID.randomUUID().toString());v.setEmail(token+"@example.test");v.setOtpCode("000000");v.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
  v.setAttempts(0);v.setMaxAttempts(5);v.setVerified(true);v.setPurpose(OtpPurpose.REGISTRATION);v.setRegistrationToken(token);v.setRegistrationTokenExpiresAt(OffsetDateTime.now().plusMinutes(5));v.setRegistrationConsumed(false);v.setLoginConsumed(false);v.setPasswordResetConsumed(false);otps.saveAndFlush(v);return token;
 }
 private com.fasterxml.jackson.databind.JsonNode register(String type) throws Exception {
  String body=json.writeValueAsString(Map.of("accountType",type,"registrationToken",verified(),"fullName","Owner integration","password","OwnerTest123!","acceptPrivacyPolicy",true));
  return json.readTree(mvc.perform(post("/api/auth/register/complete").contentType("application/json").content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
 }
 @Test void verifiedOwnerCanOnboardMultipleSpasAndManageOnlyTheirData() throws Exception {
  var registration=register("SPA_OWNER");long userId=registration.get("user").get("id").asLong();
  assertTrue(registration.get("roles").toString().contains("ROLE_SPA_OWNER"));assertFalse(registration.get("roles").toString().contains("ADMIN"));
  String credentials=json.writeValueAsString(Map.of("username",registration.get("user").get("email").asText(),"password","OwnerTest123!"));
  var login=json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json").content(credentials)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  assertTrue(login.get("roles").toString().contains("ROLE_SPA_OWNER"));
  mvc.perform(get("/api/onboarding/spas").header("Authorization","Bearer "+login.get("accessToken").asText())).andExpect(status().isOk());
  var owner=auth(userId,"ROLE_SPA_OWNER");var stranger=auth(999999L,"ROLE_SPA_OWNER");
  long[] ids=new long[2];
  for(int i=0;i<2;i++){
   var body=mvc.perform(post("/api/onboarding/spas").with(authentication(owner)).contentType("application/json").content("{\"name\":\"Owner test spa "+i+"\",\"phone\":\"1234567890\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
   var spa=json.readTree(body);ids[i]=spa.get("id").asLong();assertFalse(spa.get("active").asBoolean());assertFalse(spa.get("verified").asBoolean());
  }
  long spaId=ids[0];
  for(String step:List.of("details","address","kyc")){
   String payload=step.equals("details")?"{\"description\":\"Wellness centre\"}":step.equals("address")?"{\"addressLine\":\"Test street\",\"city\":\"Delhi\",\"timezone\":\"Asia/Kolkata\",\"countryCode\":\"IN\"}":"{\"ownerName\":\"Test Owner\"}";
   mvc.perform(put("/api/onboarding/spas/"+spaId+"/"+step).with(authentication(owner)).contentType("application/json").content(payload)).andExpect(status().isOk());
   mvc.perform(put("/api/onboarding/spas/"+spaId+"/"+step).with(authentication(stranger)).contentType("application/json").content(payload)).andExpect(status().isNotFound());
  }
  String base="/api/owner/spas/"+spaId;
  var service=mvc.perform(post(base+"/services").with(authentication(owner)).contentType("application/json").content("{\"name\":\"Massage\",\"durationMin\":60,\"bufferMin\":10,\"basePriceCents\":10000,\"currency\":\"INR\",\"active\":true}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  long serviceId=json.readTree(service).get("id").asLong();
  var t=json.createObjectNode().put("name","Therapist one").put("type","massage").put("active",true).put("available",true);t.putArray("serviceIds").add(serviceId);
  var created=json.readTree(mvc.perform(post(base+"/therapists").with(authentication(owner)).contentType("application/json").content(t.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  long tid=created.get("id").asLong();t.put("version",created.get("version").asLong()).put("name","Updated name").put("available",false);
  mvc.perform(put(base+"/therapists/"+tid).with(authentication(owner)).contentType("application/json").content(t.toString())).andExpect(status().isOk());
  mvc.perform(put(base+"/therapists/"+tid).with(authentication(owner)).contentType("application/json").content(t.toString())).andExpect(status().isConflict());
  mvc.perform(post("/api/owner/spas/"+ids[1]+"/therapists").with(authentication(owner)).contentType("application/json").content(t.toString())).andExpect(status().isBadRequest());
  mvc.perform(put("/api/owner/spas/"+ids[1]+"/therapists/"+tid).with(authentication(owner)).contentType("application/json").content(t.toString())).andExpect(status().isNotFound());
  for(String path:List.of(base+"/therapists",base+"/services","/api/admin/spas/"+spaId+"/bookings","/api/spas/"+spaId+"/settings")){
   mvc.perform(get(path).with(authentication(owner))).andExpect(status().isOk());
   mvc.perform(get(path).with(authentication(stranger))).andExpect(status().isForbidden());
   mvc.perform(get(path).with(authentication(auth(userId,"ROLE_USER")))).andExpect(status().isForbidden());
  }
  mvc.perform(post(base+"/therapists").with(authentication(stranger)).contentType("application/json").content(t.toString())).andExpect(status().isForbidden());
  mvc.perform(post("/api/spas").with(authentication(owner)).contentType("application/json").content("{}")).andExpect(status().is4xxClientError());
  String pkg="{\"level\":\"GOLD\",\"price\":1000,\"freeSessionsCount\":3,\"status\":\"ACTIVE\"}";
  mvc.perform(post("/api/v1/spa/"+spaId+"/packages").with(authentication(owner)).contentType("application/json").content(pkg)).andExpect(status().isOk());
  mvc.perform(post("/api/v1/spa/"+spaId+"/packages").with(authentication(stranger)).contentType("application/json").content(pkg)).andExpect(status().is4xxClientError());
  String inventory="{\"spaId\":"+spaId+",\"serviceId\":"+serviceId+",\"serviceDate\":\"2027-01-01\",\"availableUnits\":4,\"basePriceCents\":10000}";
  mvc.perform(post("/api/admin/inventory/day").with(authentication(owner)).contentType("application/json").content(inventory)).andExpect(status().isOk());
  mvc.perform(post("/api/admin/inventory/day").with(authentication(stranger)).contentType("application/json").content(inventory)).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/inventory/day").with(authentication(owner)).contentType("application/json").content(inventory)).andExpect(status().isOk());
  mvc.perform(post("/api/admin/inventory/day").with(authentication(owner)).contentType("application/json").content(inventory.replace("\"spaId\":"+spaId, "\"spaId\":"+ids[1]))).andExpect(status().is4xxClientError());
  var settings=json.readTree(mvc.perform(get("/api/spas/"+spaId+"/settings").with(authentication(owner))).andReturn().getResponse().getContentAsString());
  var edit=(com.fasterxml.jackson.databind.node.ObjectNode)settings;edit.put("active",true).put("openTimeLocal","09:00:00").put("closeTimeLocal","18:00:00");
  mvc.perform(put("/api/spas/"+spaId).with(authentication(owner)).contentType("application/json").content(edit.toString())).andExpect(status().isForbidden());
 }
 @Test void customerRegistrationDoesNotGrantOwnerAccess() throws Exception {
  var r=register("CUSTOMER");assertFalse(r.get("roles").toString().contains("OWNER"));
  mvc.perform(get("/api/onboarding/spas").header("Authorization","Bearer "+r.get("accessToken").asText())).andExpect(status().isForbidden());
 }
 @Test void arbitrarySignupRolesAreRejected() throws Exception {
  String body=json.writeValueAsString(Map.of("accountType","ADMIN","registrationToken",verified(),"fullName","Role injection","password","OwnerTest123!","acceptPrivacyPolicy",true));
  mvc.perform(post("/api/auth/register/complete").contentType("application/json").content(body)).andExpect(status().isBadRequest());
 }
 @Test void ownerRegistrationRequiresVerifiedToken() throws Exception {
  mvc.perform(post("/api/auth/register/complete").contentType("application/json").content("{\"accountType\":\"SPA_OWNER\",\"registrationToken\":\"invalid\",\"fullName\":\"Owner\",\"password\":\"OwnerTest123!\",\"acceptPrivacyPolicy\":true}")).andExpect(status().isBadRequest());
 }
}

