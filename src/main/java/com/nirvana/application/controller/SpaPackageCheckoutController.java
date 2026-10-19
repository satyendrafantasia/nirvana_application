package com.nirvana.application.controller;
import com.nirvana.application.service.impl.*;
import com.nirvana.application.model.dto.RazorpayConfirmRequest;
import com.nirvana.application.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class SpaPackageCheckoutController {
 private final SpaPackageCheckoutService service;
 @PostMapping("/spa/{spaId}/packages/{packageId}/checkout") public PackageCheckoutService.Checkout start(@PathVariable Long spaId,@PathVariable Long packageId){return service.start(SecurityUtils.getCurrentUserId(),spaId,packageId);}
 @PostMapping("/user/packages/{id}/checkout") public PackageCheckoutService.Checkout resume(@PathVariable Long id){return service.resume(SecurityUtils.getCurrentUserId(),id);}
 @PostMapping("/user/packages/{id}/confirm") public PackageCheckoutService.Purchase confirm(@PathVariable Long id,@Valid @RequestBody RazorpayConfirmRequest request){return service.confirm(SecurityUtils.getCurrentUserId(),id,request);}
}
