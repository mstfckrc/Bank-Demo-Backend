package com.mustafa.repository;

import com.mustafa.entity.CompanyEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CompanyEmployeeRepository extends JpaRepository<CompanyEmployee, Long> {
    // Bir şirketin tüm çalışanlarını getirmek için:
    List<CompanyEmployee> findByCompanyId(Long companyId);

    // Bir bireyin hangi şirkette çalıştığını bulmak için:
    Optional<CompanyEmployee> findByRetailCustomerId(Long retailCustomerId);
}