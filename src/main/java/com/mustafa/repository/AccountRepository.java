package com.mustafa.repository;

import com.mustafa.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findByIban(String iban);

    // Belirli bir müşterinin tüm hesaplarını listelemek için (Profil sayfasında gerekecek)
    List<Account> findByCustomerId(Long customerId);

    boolean existsByAccountNumber(String accountNumber);

    boolean existsByIban(String iban);
}