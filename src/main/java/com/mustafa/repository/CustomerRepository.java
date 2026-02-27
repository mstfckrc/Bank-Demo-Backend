package com.mustafa.repository;

import com.mustafa.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // Security katmanı girişte (Login) kullanıcıyı bulmak için bunu kullanacak
    Optional<Customer> findByTcNo(String tcNo);

    Optional<Customer> findByEmail(String email);

    // Kayıt olurken (Register) bu TC veya Email zaten var mı diye kontrol edeceğiz
    boolean existsByTcNo(String tcNo);

    boolean existsByEmail(String email);
}