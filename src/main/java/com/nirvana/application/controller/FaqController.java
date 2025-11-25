package com.nirvana.application.controller;

import com.nirvana.application.model.dto.FaqListResponse;
import com.nirvana.application.model.dto.FaqRequest;
import com.nirvana.application.model.dto.FaqResponse;
import com.nirvana.application.service.FaqService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class FaqController {

    private final FaqService faqService;

    @GetMapping("/faqs")
    public FaqListResponse listFaqs(@RequestParam(value = "category", required = false) String category) {
        return faqService.listActiveFaqs(category);
    }

    @PostMapping("/faqs")
    public FaqResponse createFaq(@Valid @RequestBody FaqRequest request) {
        return faqService.createFaq(request);
    }
}
