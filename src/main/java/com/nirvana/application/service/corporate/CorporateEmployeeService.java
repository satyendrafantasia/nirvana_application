package com.nirvana.application.service.corporate;

import com.nirvana.application.model.corporate.CorporateEmployee;

import java.util.List;

public interface CorporateEmployeeService {
    CorporateEmployee linkEmployeeToUser(Long corporateId, String employeeEmail, Long userId);
    List<CorporateEmployee> getEmployeesByCorporate(Long corporateId);
}
