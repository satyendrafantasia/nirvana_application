package com.nirvana.application.service.impl;

import com.nirvana.application.model.SpaManager;
import com.nirvana.application.model.User;
import com.nirvana.application.repository.SpaManagerRepository;
import com.nirvana.application.service.SpaManagerService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpaManagerServiceImpl implements SpaManagerService {

    private final SpaManagerRepository SpaManagerRepository;

    @Override
    public SpaManager findByUser(User user) {
        log.info("Attempting to find SpaManager for user ID: {}", user.getId());
        return SpaManagerRepository.findByUser(user)
                .orElseThrow(() -> new EntityNotFoundException("SpaManager not found for user " + user.getUsername()));
    }
}
