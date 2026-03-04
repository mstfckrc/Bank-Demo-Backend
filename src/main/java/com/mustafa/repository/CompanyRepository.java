package com.mustafa.repository;

import com.mustafa.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByContactEmail(String contactEmail);
    boolean existsByContactEmail(String contactEmail);
    // Merkezi kimlik numarası (Vergi No) üzerinden şirket profilini bulmak için:
    Optional<Company> findByAppUser_IdentityNumber(String identityNumber);
}