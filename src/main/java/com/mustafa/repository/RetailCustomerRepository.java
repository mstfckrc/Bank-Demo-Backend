package com.mustafa.repository;

import com.mustafa.entity.RetailCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RetailCustomerRepository extends JpaRepository<RetailCustomer, Long> {
    Optional<RetailCustomer> findByEmail(String email);
    boolean existsByEmail(String email);
    // Merkezi kimlik numarası (TC) üzerinden bireysel profili bulmak için:
    Optional<RetailCustomer> findByAppUser_IdentityNumber(String identityNumber);
}