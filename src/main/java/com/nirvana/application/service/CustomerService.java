package com.nirvana.application.service;

import com.nirvana.application.model.Customer;

import java.util.Optional;

public interface CustomerService {

    Optional<Customer> findByUserId(Long userId);

    Optional<Customer> findByUsername(String username);
}
