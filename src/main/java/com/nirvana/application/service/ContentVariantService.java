package com.nirvana.application.service;

import com.nirvana.application.model.dto.ContentVariantListResponse;
import com.nirvana.application.model.dto.ContentVariantRequest;
import com.nirvana.application.model.dto.ContentVariantResponse;

public interface ContentVariantService {

    ContentVariantListResponse listActiveVariants(String experimentKey);

    ContentVariantResponse createVariant(ContentVariantRequest request);
}
