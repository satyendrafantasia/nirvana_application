package com.nirvana.application.service;

import com.nirvana.application.model.enums.spa.SpaPackageLevel;

public interface SpaPackageNotificationService {

    void notifySpaOwnerOfPackagePurchase(Long spaId, Long userId, Long subscriptionId, SpaPackageLevel level);
}
