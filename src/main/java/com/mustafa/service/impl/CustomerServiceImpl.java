package com.mustafa.service.impl;

import com.mustafa.dto.request.ChangePasswordRequest;
import com.mustafa.dto.request.UpdateProfileRequest;
import com.mustafa.dto.response.UserProfileResponse;
import com.mustafa.entity.AppUser;
import com.mustafa.entity.Company;
import com.mustafa.entity.RetailCustomer;
import com.mustafa.exception.BankOperationException;
import com.mustafa.repository.AppUserRepository;
import com.mustafa.repository.CompanyRepository;
import com.mustafa.repository.RetailCustomerRepository;
import com.mustafa.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final AppUserRepository appUserRepository;
    private final RetailCustomerRepository retailCustomerRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    private AppUser getAuthenticatedAppUser() {
        String identityNumber = SecurityContextHolder.getContext().getAuthentication().getName();
        return appUserRepository.findByIdentityNumber(identityNumber)
                .orElseThrow(() -> new BankOperationException("Kullanıcı bulunamadı!"));
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        AppUser appUser = getAuthenticatedAppUser();
        String profileName = "";
        String email = "";

        if (appUser.getRole() == AppUser.Role.RETAIL_CUSTOMER) {
            RetailCustomer retail = retailCustomerRepository.findByAppUser_IdentityNumber(appUser.getIdentityNumber()).get();

            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                retail.setEmail(request.getEmail());
            }

            // 🚀 DÜZELTME: Gelen tek parça string'i Ad ve Soyad olarak ikiye bölüyoruz
            if (request.getProfileName() != null && request.getProfileName().trim().length() >= 3) {
                String fullName = request.getProfileName().trim();
                int lastSpaceIndex = fullName.lastIndexOf(" ");

                if (lastSpaceIndex == -1) {
                    // Sadece tek isim girildiyse (Örn: "Mustafa")
                    retail.setFirstName(fullName);
                    retail.setLastName(""); // Eski soyadı temizliyoruz ki "ÇÇ" asılı kalmasın!
                } else {
                    // Son boşluğa kadar olan kısım Ad, sonrası Soyad
                    retail.setFirstName(fullName.substring(0, lastSpaceIndex).trim());
                    retail.setLastName(fullName.substring(lastSpaceIndex + 1).trim());
                }
            }
            retailCustomerRepository.save(retail);

            // 🚀 DÜZELTME: Soyad boş kalırsa fazladan boşluk dönmesin diye trim() ekledik
            profileName = (retail.getFirstName() + " " + retail.getLastName()).trim();
            email = retail.getEmail();

        } else if (appUser.getRole() == AppUser.Role.CORPORATE_MANAGER) {
            Company company = companyRepository.findByAppUser_IdentityNumber(appUser.getIdentityNumber()).get();

            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                company.setContactEmail(request.getEmail());
            }

            // Kurumsal müşterilerde zaten tek parça şirket adı var, bölmeye gerek yok
            if (request.getProfileName() != null && request.getProfileName().trim().length() >= 3) {
                company.setCompanyName(request.getProfileName().trim());
            }
            companyRepository.save(company);

            profileName = company.getCompanyName();
            email = company.getContactEmail();
        }

        return UserProfileResponse.builder()
                .identityNumber(appUser.getIdentityNumber())
                .profileName(profileName)
                .email(email)
                .role(appUser.getRole().name())
                .status(appUser.getStatus().name())
                .build();
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        AppUser appUser = getAuthenticatedAppUser();
        if (!passwordEncoder.matches(request.getOldPassword(), appUser.getPassword())) {
            throw new BankOperationException("Eski şifreniz hatalı!");
        }
        if (request.getNewPassword().length() < 6) {
            throw new BankOperationException("Yeni şifreniz en az 6 karakter olmalıdır!");
        }
        appUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        appUserRepository.save(appUser);
    }

    @Override
    public UserProfileResponse getMyProfile() {
        AppUser appUser = getAuthenticatedAppUser();
        String profileName = "";
        String email = "";

        if (appUser.getRole() == AppUser.Role.RETAIL_CUSTOMER) {
            RetailCustomer retail = retailCustomerRepository.findByAppUser_IdentityNumber(appUser.getIdentityNumber()).get();
            profileName = retail.getFirstName() + " " + retail.getLastName();
            email = retail.getEmail();
        } else if (appUser.getRole() == AppUser.Role.CORPORATE_MANAGER) {
            Company company = companyRepository.findByAppUser_IdentityNumber(appUser.getIdentityNumber()).get();
            profileName = company.getCompanyName();
            email = company.getContactEmail();
        } else {
            profileName = "Sistem Yöneticisi";
            email = "admin@bank.com";
        }

        return UserProfileResponse.builder()
                .identityNumber(appUser.getIdentityNumber())
                .profileName(profileName)
                .email(email)
                .role(appUser.getRole().name())
                .status(appUser.getStatus().name())
                .build();
    }

    @Override
    @Transactional
    public void appealRejection() {
        AppUser appUser = getAuthenticatedAppUser();
        if (appUser.getStatus() != AppUser.ApprovalStatus.REJECTED) {
            throw new BankOperationException("Sadece reddedilen hesaplar yeniden değerlendirme talebinde bulunabilir.");
        }
        appUser.setStatus(AppUser.ApprovalStatus.PENDING);
        appUserRepository.save(appUser);
    }
}