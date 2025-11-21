package com.nirvana.application.service;

import com.nirvana.application.model.dto.SpaRequestDTO;
import com.nirvana.application.model.dto.SpaResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SpaService {

    SpaResponseDTO createSpa(SpaRequestDTO request);

    SpaResponseDTO getSpaById(Long id);

    Page<SpaResponseDTO> listSpas(Pageable pageable);

    SpaResponseDTO updateSpa(Long id, SpaRequestDTO request);

    void deactivateSpa(Long id); // soft delete
}
