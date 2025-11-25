package com.nirvana.application.repository;

import com.nirvana.application.model.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    boolean existsByCode(String code);
}
