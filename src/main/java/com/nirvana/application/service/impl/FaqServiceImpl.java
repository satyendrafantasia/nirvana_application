package com.nirvana.application.service.impl;

import com.nirvana.application.model.Faq;
import com.nirvana.application.model.dto.FaqListResponse;
import com.nirvana.application.model.dto.FaqRequest;
import com.nirvana.application.model.dto.FaqResponse;
import com.nirvana.application.repository.FaqRepository;
import com.nirvana.application.service.FaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FaqServiceImpl implements FaqService {

    private final FaqRepository faqRepository;

    @Override
    @Transactional(readOnly = true)
    public FaqListResponse listActiveFaqs(String category) {
        List<Faq> faqs = category == null
                ? faqRepository.findByIsActiveTrueOrderByCreatedAtDesc()
                : faqRepository.findByIsActiveTrueAndCategoryOrderByCreatedAtDesc(category);
        List<FaqResponse> responses = faqs.stream()
                .map(this::toResponse)
                .toList();
        return new FaqListResponse(responses);
    }

    @Override
    @Transactional
    public FaqResponse createFaq(FaqRequest request) {
        Faq faq = new Faq();
        faq.setQuestion(request.getQuestion());
        faq.setAnswer(request.getAnswer());
        faq.setCategory(request.getCategory());
        faq.setIsActive(request.getIsActive() == null ? Boolean.TRUE : request.getIsActive());
        Faq saved = faqRepository.save(faq);
        return toResponse(saved);
    }

    private FaqResponse toResponse(Faq faq) {
        return new FaqResponse(
                faq.getId(),
                faq.getQuestion(),
                faq.getAnswer(),
                faq.getCategory(),
                faq.getIsActive()
        );
    }
}
