package com.nirvana.application.service.impl.corporate;

import com.nirvana.application.exception.CorporateEmployeeNotFoundException;
import com.nirvana.application.exception.CorporateNotFoundException;
import com.nirvana.application.model.User;
import com.nirvana.application.model.corporate.Corporate;
import com.nirvana.application.model.corporate.CorporateEmployee;
import com.nirvana.application.model.enums.CorporateEmployeeStatus;
import com.nirvana.application.repository.corporate.CorporateEmployeeRepository;
import com.nirvana.application.repository.corporate.CorporateRepository;
import com.nirvana.application.service.corporate.CorporateEmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CorporateEmployeeServiceImpl implements CorporateEmployeeService {

    private final CorporateRepository corporateRepository;
    private final CorporateEmployeeRepository corporateEmployeeRepository;

    @Override
    public CorporateEmployee linkEmployeeToUser(Long corporateId, String employeeEmail, Long userId) {
        Corporate corporate = corporateRepository.findById(corporateId)
                .orElseThrow(() -> new CorporateNotFoundException(corporateId));
        CorporateEmployee employee = corporateEmployeeRepository.findByCorporateIdAndEmployeeEmail(corporateId, employeeEmail)
                .orElseThrow(() -> new CorporateEmployeeNotFoundException(employeeEmail));
        employee.setCorporate(corporate);
        employee.setUser(User.builder().id(userId).build());
        employee.setStatus(CorporateEmployeeStatus.ACTIVE);
        return corporateEmployeeRepository.save(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CorporateEmployee> getEmployeesByCorporate(Long corporateId) {
        if (!corporateRepository.existsById(corporateId)) {
            throw new CorporateNotFoundException(corporateId);
        }
        return corporateEmployeeRepository.findByCorporateId(corporateId);
    }
}
