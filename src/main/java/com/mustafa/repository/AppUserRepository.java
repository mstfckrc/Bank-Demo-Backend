package com.mustafa.repository;

import com.mustafa.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByIdentityNumber(String identityNumber);
    boolean existsByIdentityNumber(String identityNumber);
}