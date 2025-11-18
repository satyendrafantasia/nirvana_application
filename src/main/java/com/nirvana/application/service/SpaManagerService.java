package com.nirvana.application.service;

import com.nirvana.application.model.SpaManager;
import com.nirvana.application.model.User;

public interface SpaManagerService {

    SpaManager findByUser(User user);

}
