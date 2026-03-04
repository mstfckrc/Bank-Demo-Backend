package com.mustafa.service.impl;

import com.mustafa.dto.request.LoginRequest;
import com.mustafa.dto.request.RegisterRequest;
import com.mustafa.dto.response.AuthResponse;
import com.mustafa.entity.AppUser;
import com.mustafa.entity.Company;
import com.mustafa.entity.RetailCustomer;
import com.mustafa.exception.BankOperationException;
import com.mustafa.repository.AppUserRepository;
import com.mustafa.repository.CompanyRepository;
import com.mustafa.repository.RetailCustomerRepository;
import com.mustafa.security.JwtService;
import com.mustafa.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AppUserRepository appUserRepository;
    private final RetailCustomerRepository retailCustomerRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 1. KONTROL: Bu Kimlik/Vergi No daha önce kullanılmış mı?
        if (appUserRepository.existsByIdentityNumber(request.getIdentityNumber())) {
            throw new BankOperationException("Bu Kimlik/Vergi Numarası sistemde zaten kayıtlı!");
        }

        // 2. Merkezi Kimliği (AppUser) Oluştur
        AppUser appUser = AppUser.builder()
                .identityNumber(request.getIdentityNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(AppUser.Role.valueOf(request.getRole()))
                .status(AppUser.ApprovalStatus.PENDING) // Herkes beklemede başlar
                .build();

        appUserRepository.save(appUser);

        // 3. Fabrika Mantığı: Rol'e Göre Profil Oluştur
        if (appUser.getRole() == AppUser.Role.RETAIL_CUSTOMER) {
            if (retailCustomerRepository.existsByEmail(request.getEmail())) {
                throw new BankOperationException("Bu Email adresi zaten kullanımda!");
            }
            RetailCustomer retailCustomer = RetailCustomer.builder()
                    .appUser(appUser)
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .build();
            retailCustomerRepository.save(retailCustomer);

        } else if (appUser.getRole() == AppUser.Role.CORPORATE_MANAGER) {
            if (companyRepository.existsByContactEmail(request.getEmail())) {
                throw new BankOperationException("Bu Email adresi zaten kullanımda!");
            }
            Company company = Company.builder()
                    .appUser(appUser)
                    .companyName(request.getCompanyName())
                    .taxOffice(request.getTaxOffice())
                    .contactEmail(request.getEmail())
                    .build();
            companyRepository.save(company);

        } else {
            throw new BankOperationException("Geçersiz veya yetkisiz rol seçimi!");
        }

        // 4. Yeni kullanıcıya JWT Token üret ve dön
        String jwtToken = jwtService.generateToken(appUser);
        return AuthResponse.builder().token(jwtToken).message("Kayıt işlemi başarıyla gerçekleşti.").build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // 1. Spring Security, IdentityNumber ve Şifreyi doğrular
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getIdentityNumber(), request.getPassword())
        );

        // 2. Şifre doğruysa kullanıcıyı çek
        AppUser appUser = appUserRepository.findByIdentityNumber(request.getIdentityNumber())
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));

        // 3. Token üret ve dön
        String jwtToken = jwtService.generateToken(appUser);
        return AuthResponse.builder().token(jwtToken).message("Giriş başarılı.").build();
    }
}