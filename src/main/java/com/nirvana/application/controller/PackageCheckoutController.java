package com.nirvana.application.controller;
import com.nirvana.application.service.impl.PackageCheckoutService;
import com.nirvana.application.model.dto.RazorpayConfirmRequest;
import com.nirvana.application.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/packages") @RequiredArgsConstructor
public class PackageCheckoutController {
 private final PackageCheckoutService service;
 /** Public, the same for everyone, and slow to change: browsers may reuse it for five minutes. */
 private static final org.springframework.http.CacheControl PUBLIC_FIVE_MINUTES=org.springframework.http.CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic();
 @GetMapping("/catalog") public org.springframework.http.ResponseEntity<List<PackageCheckoutService.CatalogItem>> catalog(@RequestParam(required=false) Long spaId){
  return org.springframework.http.ResponseEntity.ok().cacheControl(PUBLIC_FIVE_MINUTES).body(service.catalog(spaId));}
 /** Which published spas take Nirvana packages: searched by name or city, optionally for one package. */
 @GetMapping("/spas") public org.springframework.http.ResponseEntity<PackageCheckoutService.SpaMatches> spas(@RequestParam(required=false) String q,
   @RequestParam(required=false) Long packageId,@RequestParam(defaultValue="50") int limit){
  return org.springframework.http.ResponseEntity.ok().cacheControl(PUBLIC_FIVE_MINUTES).body(service.findSpas(q,packageId,limit));}
 @GetMapping("/mine") public List<PackageCheckoutService.Purchase> mine(){return service.mine(SecurityUtils.getCurrentUserId());}
 @PostMapping("/{id}/checkout") public PackageCheckoutService.Checkout start(@PathVariable Long id){return service.start(SecurityUtils.getCurrentUserId(),id);}
 @PostMapping("/mine/{id}/checkout") public PackageCheckoutService.Checkout resume(@PathVariable Long id){return service.resume(SecurityUtils.getCurrentUserId(),id);}
 @PostMapping("/mine/{id}/confirm") public PackageCheckoutService.Purchase confirm(@PathVariable Long id,@Valid @RequestBody RazorpayConfirmRequest request){return service.confirm(SecurityUtils.getCurrentUserId(),id,request);}
}
