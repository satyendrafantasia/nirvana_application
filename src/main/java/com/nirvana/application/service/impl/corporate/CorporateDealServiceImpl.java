package com.nirvana.application.service.impl.corporate;

import com.nirvana.application.exception.CorporateDealNotFoundException;
import com.nirvana.application.exception.CorporateNotFoundException;
import com.nirvana.application.model.corporate.Corporate;
import com.nirvana.application.model.corporate.CorporateDeal;
import com.nirvana.application.model.dto.corporate.CorporateDealResponse;
import com.nirvana.application.model.dto.corporate.CreateCorporateDealRequest;
import com.nirvana.application.model.enums.CorporateDealStatus;
import com.nirvana.application.model.enums.CorporatePaymentStatus;
import com.nirvana.application.repository.corporate.CorporateDealRepository;
import com.nirvana.application.repository.corporate.CorporateRepository;
import com.nirvana.application.service.corporate.CorporateDealService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CorporateDealServiceImpl implements CorporateDealService {

    private final CorporateRepository corporateRepository;
    private final CorporateDealRepository corporateDealRepository;

    @Override
    public CorporateDealResponse createCorporateDeal(Long corporateId, CreateCorporateDealRequest request) {
        Corporate corporate = corporateRepository.findById(corporateId)
                .orElseThrow(() -> new CorporateNotFoundException(corporateId));
        CorporateDeal deal = CorporateDeal.builder()
                .corporate(corporate)
                .dealName(request.dealName())
                .description(request.description())
                .couponType(request.couponType())
                .totalSessionsPerEmployee(request.totalSessionsPerEmployee())
                .globalPackageType(request.globalPackageType())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();
        corporateDealRepository.save(deal);
        return toResponse(deal);
    }

    @Override
    public CorporateDealResponse updateCorporateDeal(Long corporateId, Long dealId, CreateCorporateDealRequest request) {
        CorporateDeal deal = corporateDealRepository.findById(dealId)
                .orElseThrow(() -> new CorporateDealNotFoundException(dealId));
        if (!deal.getCorporate().getId().equals(corporateId)) {
            throw new CorporateNotFoundException(corporateId);
        }
        deal.setDealName(request.dealName());
        deal.setDescription(request.description());
        deal.setCouponType(request.couponType());
        deal.setTotalSessionsPerEmployee(request.totalSessionsPerEmployee());
        deal.setGlobalPackageType(request.globalPackageType());
        deal.setStartDate(request.startDate());
        deal.setEndDate(request.endDate());
        return toResponse(deal);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CorporateDealResponse> listCorporateDeals(Long corporateId) {
        return corporateDealRepository.findByCorporateId(corporateId).stream().map(this::toResponse).toList();
    }

    @Override
    public CorporateDealResponse activateDeal(Long corporateId, Long dealId) {
        CorporateDeal deal = corporateDealRepository.findById(dealId)
                .orElseThrow(() -> new CorporateDealNotFoundException(dealId));
        if (!deal.getCorporate().getId().equals(corporateId)) {
            throw new CorporateNotFoundException(corporateId);
        }
        deal.setStatus(CorporateDealStatus.ACTIVE);
        return toResponse(deal);
    }

    @Override
    public CorporateDealResponse deactivateDeal(Long corporateId, Long dealId) {
        CorporateDeal deal = corporateDealRepository.findById(dealId)
                .orElseThrow(() -> new CorporateDealNotFoundException(dealId));
        if (!deal.getCorporate().getId().equals(corporateId)) {
            throw new CorporateNotFoundException(corporateId);
        }
        deal.setStatus(CorporateDealStatus.INACTIVE);
        return toResponse(deal);
    }

    @Override
    public CorporateDealResponse confirmPayment(Long corporateId, Long dealId) {
        CorporateDeal deal = corporateDealRepository.findById(dealId)
                .orElseThrow(() -> new CorporateDealNotFoundException(dealId));
        if (!deal.getCorporate().getId().equals(corporateId)) {
            throw new CorporateNotFoundException(corporateId);
        }
        deal.setCorporatePaymentStatus(CorporatePaymentStatus.PAID);
        return toResponse(deal);
    }

    private CorporateDealResponse toResponse(CorporateDeal deal) {
        return new CorporateDealResponse(
                deal.getId(),
                deal.getCorporate().getId(),
                deal.getDealName(),
                deal.getDescription(),
                deal.getCouponType(),
                deal.getTotalSessionsPerEmployee(),
                deal.getGlobalPackageType(),
                deal.getStartDate(),
                deal.getEndDate(),
                deal.getStatus(),
                deal.getCorporatePaymentStatus()
        );
    }
}
