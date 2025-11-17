package com.nirvana.application.service;

import com.nirvana.application.model.HotelManager;
import com.nirvana.application.model.User;

public interface HotelManagerService {

    HotelManager findByUser(User user);

}
