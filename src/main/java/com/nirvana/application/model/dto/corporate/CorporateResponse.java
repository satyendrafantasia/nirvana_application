package com.nirvana.application.model.dto.corporate;

import com.nirvana.application.model.enums.CorporateDealStatus;

public record CorporateResponse(Long id, String name, String domain, String contactPerson, String contactEmail, CorporateDealStatus status) {
}
