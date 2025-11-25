package com.nirvana.application.service;

import com.nirvana.application.model.dto.FaqListResponse;
import com.nirvana.application.model.dto.FaqRequest;
import com.nirvana.application.model.dto.FaqResponse;

public interface FaqService {

    FaqListResponse listActiveFaqs(String category);

    FaqResponse createFaq(FaqRequest request);
}
