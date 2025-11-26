package com.nirvana.application.repository.corporate;

import com.nirvana.application.model.corporate.CorporateEmployee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CorporateEmployeeRepository extends JpaRepository<CorporateEmployee, Long> {
    Optional<CorporateEmployee> findByCorporateIdAndEmployeeEmail(Long corporateId, String employeeEmail);
    List<CorporateEmployee> findByCorporateId(Long corporateId);
    List<CorporateEmployee> findByUserId(Long userId);
}
