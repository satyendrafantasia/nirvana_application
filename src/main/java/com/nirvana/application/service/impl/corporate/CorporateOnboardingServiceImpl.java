package com.nirvana.application.service.impl.corporate;

import com.nirvana.application.exception.CorporateDealNotFoundException;
import com.nirvana.application.exception.CorporateNotFoundException;
import com.nirvana.application.exception.CorporateOnboardingParseException;
import com.nirvana.application.exception.CorporatePaymentPendingException;
import com.nirvana.application.model.Role;
import com.nirvana.application.model.User;
import com.nirvana.application.model.corporate.Corporate;
import com.nirvana.application.model.corporate.CorporateDeal;
import com.nirvana.application.model.corporate.CorporateEmployee;
import com.nirvana.application.model.corporate.CorporateEmployeeCoupon;
import com.nirvana.application.model.corporate.CorporateOnboardingUpload;
import com.nirvana.application.model.dto.corporate.CorporateEmployeeUploadResponse;
import com.nirvana.application.model.dto.corporate.CorporateOnboardingUploadStatusResponse;
import com.nirvana.application.model.enums.CorporateCouponStatus;
import com.nirvana.application.model.enums.CorporateEmployeeStatus;
import com.nirvana.application.model.enums.CorporateOnboardingStatus;
import com.nirvana.application.model.enums.CorporatePaymentStatus;
import com.nirvana.application.model.enums.RoleType;
import com.nirvana.application.repository.RoleRepository;
import com.nirvana.application.repository.UserRepository;
import com.nirvana.application.repository.corporate.*;
import com.nirvana.application.service.EmailService;
import com.nirvana.application.service.corporate.CorporateOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional
public class CorporateOnboardingServiceImpl implements CorporateOnboardingService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final CorporateRepository corporateRepository;
    private final CorporateDealRepository corporateDealRepository;
    private final CorporateEmployeeRepository corporateEmployeeRepository;
    private final CorporateEmployeeCouponRepository corporateEmployeeCouponRepository;
    private final CorporateOnboardingUploadRepository onboardingUploadRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Override
    public CorporateEmployeeUploadResponse uploadEmployeeFile(Long corporateId, Long corporateDealId, MultipartFile file) {
        Corporate corporate = corporateRepository.findById(corporateId)
                .orElseThrow(() -> new CorporateNotFoundException(corporateId));
        CorporateDeal deal = corporateDealRepository.findById(corporateDealId)
                .orElseThrow(() -> new CorporateDealNotFoundException(corporateDealId));

        if (!CorporatePaymentStatus.PAID.equals(deal.getCorporatePaymentStatus())) {
            CorporateOnboardingUpload pendingUpload = CorporateOnboardingUpload.builder()
                    .corporate(corporate)
                    .corporateDeal(deal)
                    .originalFileName(file.getOriginalFilename())
                    .status(CorporateOnboardingStatus.PENDING_PAYMENT)
                    .totalRecords(0)
                    .successCount(0)
                    .failureCount(0)
                    .build();
            onboardingUploadRepository.save(pendingUpload);
            throw new CorporatePaymentPendingException("Corporate deal payment is not confirmed. Admin approval required before onboarding.");
        }

        CorporateOnboardingUpload upload = CorporateOnboardingUpload.builder()
                .corporate(corporate)
                .corporateDeal(deal)
                .originalFileName(file.getOriginalFilename())
                .status(CorporateOnboardingStatus.PROCESSED)
                .build();
        onboardingUploadRepository.save(upload);

        List<String> failures = new ArrayList<>();
        int success = 0;
        Set<String> seenEmails = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.toLowerCase(Locale.ROOT).startsWith("employee_email")) {
                    continue;
                }
                String[] parts = trimmed.split(",");
                String email = parts[0].trim();
                if (seenEmails.contains(email)) {
                    continue;
                }
                seenEmails.add(email);
                if (!EMAIL_PATTERN.matcher(email).matches()) {
                    failures.add("Invalid email: " + email);
                    continue;
                }
                try {
                    processEmployeeEmail(email, corporate, deal);
                    success++;
                } catch (Exception ex) {
                    failures.add("Failed for " + email + ": " + ex.getMessage());
                }
            }
        } catch (IOException e) {
            upload.setStatus(CorporateOnboardingStatus.FAILED);
            onboardingUploadRepository.save(upload);
            throw new CorporateOnboardingParseException("Unable to read uploaded file");
        }

        upload.setTotalRecords(seenEmails.size());
        upload.setSuccessCount(success);
        upload.setFailureCount(failures.size());
        if (!failures.isEmpty() && success > 0) {
            upload.setStatus(CorporateOnboardingStatus.PARTIALLY_PROCESSED);
        } else if (success == 0 && !failures.isEmpty()) {
            upload.setStatus(CorporateOnboardingStatus.FAILED);
        }
        onboardingUploadRepository.save(upload);

        return new CorporateEmployeeUploadResponse(upload.getId(), seenEmails.size(), success, failures.size(), failures);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CorporateOnboardingUploadStatusResponse> listUploads(Long corporateId) {
        return onboardingUploadRepository.findByCorporateIdOrderByCreatedAtDesc(corporateId)
                .stream()
                .map(upload -> new CorporateOnboardingUploadStatusResponse(
                        upload.getId(),
                        upload.getCorporate().getId(),
                        upload.getCorporateDeal().getId(),
                        upload.getOriginalFileName(),
                        upload.getStatus(),
                        upload.getTotalRecords(),
                        upload.getSuccessCount(),
                        upload.getFailureCount(),
                        upload.getCreatedAt()
                ))
                .toList();
    }

    private void processEmployeeEmail(String email, Corporate corporate, CorporateDeal deal) {
        Optional<User> existingUser = userRepository.findByEmail(email);
        User user = existingUser.orElseGet(() -> createUserFromCorporate(email));
        CorporateEmployee employee = corporateEmployeeRepository
                .findByCorporateIdAndEmployeeEmail(corporate.getId(), email)
                .orElseGet(() -> CorporateEmployee.builder()
                        .corporate(corporate)
                        .employeeEmail(email)
                        .status(CorporateEmployeeStatus.INVITED)
                        .build());
        employee.setUser(user);
        employee.setStatus(CorporateEmployeeStatus.ACTIVE);
        corporateEmployeeRepository.save(employee);

        CorporateEmployeeCoupon coupon = CorporateEmployeeCoupon.builder()
                .corporate(corporate)
                .corporateDeal(deal)
                .corporateEmployee(employee)
                .user(user)
                .couponType(deal.getCouponType())
                .totalSessions(deal.getTotalSessionsPerEmployee())
                .remainingSessions(deal.getTotalSessionsPerEmployee())
                .globalPackageType(deal.getGlobalPackageType())
                .startDate(Optional.ofNullable(deal.getStartDate()).orElse(LocalDate.now()))
                .expiryDate(deal.getEndDate())
                .status(CorporateCouponStatus.ACTIVE)
                .build();
        corporateEmployeeCouponRepository.save(coupon);

        if (existingUser.isPresent()) {
            emailService.sendCorporateBenefitsActivated(email, deal.getDealName());
        }
    }

    private User createUserFromCorporate(String email) {
        String rawPassword = UUID.randomUUID().toString();
        Role role = roleRepository.findByRoleType(RoleType.CUSTOMER);
        User user = User.builder()
                .email(email)
                .username(email)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .roles(Set.of(RoleType.CUSTOMER.name()))
                .active(true)
                .isActive(true)
                .build();
        userRepository.save(user);
        emailService.sendCorporateEmployeeInvite(email, email, rawPassword);
        return user;
    }
}
