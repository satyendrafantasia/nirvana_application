package com.nirvana.application.service;

import com.nirvana.application.model.Spa;
import com.nirvana.application.model.dto.SpaDTO;
import com.nirvana.application.model.dto.SpaRegistrationDTO;

import java.util.List;
import java.util.Optional;

public interface SpaService {

    Spa saveSpa(SpaRegistrationDTO SpaRegistrationDTO);

    SpaDTO findSpaDtoByName(String name);

    SpaDTO findSpaDtoById(Long id);

    Optional<Spa> findSpaById(Long id);

    List<SpaDTO> findAllSpas();

    SpaDTO updateSpa(SpaDTO SpaDTO);

    void deleteSpaById(Long id);

    List<Spa> findAllSpasByManagerId(Long managerId);

    List<SpaDTO> findAllSpaDtosByManagerId(Long managerId);

    SpaDTO findSpaByIdAndManagerId(Long SpaId, Long managerId);

    SpaDTO updateSpaByManagerId(SpaDTO SpaDTO, Long managerId);

    void deleteSpaByIdAndManagerId(Long SpaId, Long managerId);

    SpaDTO mapSpaToSpaDto(Spa Spa);

}
