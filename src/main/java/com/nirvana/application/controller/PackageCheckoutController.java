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
 @GetMapping("/catalog") public List<PackageCheckoutService.CatalogItem> catalog(){return service.catalog();}
 @GetMapping("/mine") public List<PackageCheckoutService.Purchase> mine(){return service.mine(SecurityUtils.getCurrentUserId());}
 @PostMapping("/{id}/checkout") public PackageCheckoutService.Checkout start(@PathVariable Long id){return service.start(SecurityUtils.getCurrentUserId(),id);}
 @PostMapping("/mine/{id}/checkout") public PackageCheckoutService.Checkout resume(@PathVariable Long id){return service.resume(SecurityUtils.getCurrentUserId(),id);}
 @PostMapping("/mine/{id}/confirm") public PackageCheckoutService.Purchase confirm(@PathVariable Long id,@Valid @RequestBody RazorpayConfirmRequest request){return service.confirm(SecurityUtils.getCurrentUserId(),id,request);}
}
