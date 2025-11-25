package com.nirvana.application.repository;

import com.nirvana.application.model.NotificationTemplate;
import com.nirvana.application.model.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByCodeAndChannelAndEnabledIsTrue(String code, NotificationChannel channel);

    Optional<NotificationTemplate> findByCodeAndChannel(String code, NotificationChannel channel);

    List<NotificationTemplate> findAllByEnabledIsTrue();
}
