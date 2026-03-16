package com.mustafa.service.impl;

import com.mustafa.config.RabbitMQPublisher;
import com.mustafa.dto.request.LoginRequest;
import com.mustafa.dto.request.RegisterRequest;
import com.mustafa.dto.response.AuthResponse;
import com.mustafa.entity.AppUser;
import com.mustafa.entity.Company;
import com.mustafa.entity.RetailCustomer;
import com.mustafa.exception.BankOperationException;
import com.mustafa.repository.IAppUserRepository;
import com.mustafa.repository.ICompanyRepository;
import com.mustafa.repository.IRetailCustomerRepository;
import com.mustafa.security.JwtService;
import com.mustafa.service.IAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j // 🚀 LOGGER AKTİF
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final IAppUserRepository appUserRepository;
    private final IRetailCustomerRepository retailCustomerRepository;
    private final ICompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    private final RabbitMQPublisher rabbitPublisher;

    // 🚀 KVKK Uyumlu Loglama İçin Maskeleme Metodu (Örn: 12345678901 -> *******8901)
    private String maskIdentity(String identity) {
        if (identity == null || identity.length() <= 4) return "****";
        return "*******" + identity.substring(identity.length() - 4);
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String maskedId = maskIdentity(request.getIdentityNumber());
        log.info("Kayıt işlemi başlatıldı. Kimlik/Vergi No: {}, Rol: {}", maskedId, request.getRole());

        // 1. KONTROL
        if (appUserRepository.existsByIdentityNumber(request.getIdentityNumber())) {
            log.warn("Kayıt Reddedildi: {} kimlik numarası sistemde zaten mevcut!", maskedId);
            throw new BankOperationException("Bu Kimlik/Vergi Numarası sistemde zaten kayıtlı!");
        }

        // 2. Merkezi Kimliği Oluştur
        AppUser appUser = AppUser.builder()
                .identityNumber(request.getIdentityNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(AppUser.Role.valueOf(request.getRole()))
                .status(AppUser.ApprovalStatus.PENDING)
                .build();
        appUserRepository.save(appUser);
        log.info("Merkezi kullanıcı (AppUser) başarıyla oluşturuldu. ID: {}", appUser.getId());

        // 3. Fabrika Mantığı
        if (appUser.getRole() == AppUser.Role.RETAIL_CUSTOMER) {
            if (retailCustomerRepository.existsByEmail(request.getEmail())) {
                log.warn("Kayıt Reddedildi: {} e-posta adresi bireysel hesaplar için kullanımda!", request.getEmail());
                throw new BankOperationException("Bu Email adresi zaten kullanımda!");
            }
            RetailCustomer retailCustomer = RetailCustomer.builder()
                    .appUser(appUser)
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .build();
            retailCustomerRepository.save(retailCustomer);
            log.info("Bireysel Müşteri (RetailCustomer) profili oluşturuldu.");

        } else if (appUser.getRole() == AppUser.Role.CORPORATE_MANAGER) {
            if (companyRepository.existsByContactEmail(request.getEmail())) {
                log.warn("Kayıt Reddedildi: {} e-posta adresi kurumsal hesaplar için kullanımda!", request.getEmail());
                throw new BankOperationException("Bu Email adresi zaten kullanımda!");
            }
            Company company = Company.builder()
                    .appUser(appUser)
                    .companyName(request.getCompanyName())
                    .taxOffice(request.getTaxOffice())
                    .contactEmail(request.getEmail())
                    .build();
            companyRepository.save(company);
            log.info("Kurumsal Yönetici (Company) profili oluşturuldu. Şirket: {}", request.getCompanyName());

        } else {
            log.error("Kayıt sırasında geçersiz rol tespiti: {}", request.getRole());
            throw new BankOperationException("Geçersiz veya yetkisiz rol seçimi!");
        }

        String jwtToken = jwtService.generateToken(appUser);
        log.info("Kayıt tamamlandı. {} için JWT Token üretildi.", maskedId);

        rabbitPublisher.sendNotification("SİSTEME YENİ KAYIT | Kimlik: " + maskedId + " | Rol: " + request.getRole());

        return AuthResponse.builder().token(jwtToken).message("Kayıt işlemi başarıyla gerçekleşti.").build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String maskedId = maskIdentity(request.getIdentityNumber());
        log.info("Giriş (Login) denemesi başlatıldı. Kimlik: {}", maskedId);

        try {
            // Spring Security kapısı
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getIdentityNumber(), request.getPassword())
            );
        } catch (org.springframework.security.core.AuthenticationException e) {
            // 🚀 İŞTE YENİ İSTİHBARAT AĞIMIZ: Yanlış şifre denendiğinde burası ötecek!
            log.warn("🚨 Başarısız giriş denemesi! Hatalı şifre. Kimlik: {}", maskedId);

            // Bu mesajı dinleyen servis anında müşteriye "Hesabınıza girmeye çalıştılar" diye mail atabilir.
            rabbitPublisher.sendNotification("GÜVENLİK ALARMI! | Başarısız giriş denemesi | Kimlik: " + maskedId);

            throw new BankOperationException("Kimlik numarası veya şifre hatalı!");
        }

        AppUser appUser = appUserRepository.findByIdentityNumber(request.getIdentityNumber())
                .orElseThrow(() -> {
                    log.error("Kimlik doğrulandı ancak veritabanında AppUser bulunamadı! Kimlik: {}", maskedId);
                    return new RuntimeException("Kullanıcı bulunamadı");
                });

        String jwtToken = jwtService.generateToken(appUser);
        log.info("Giriş başarılı. {} kimlikli kullanıcı sisteme giriş yaptı.", maskedId);

        rabbitPublisher.sendNotification("SİSTEME GİRİŞ YAPILDI | Kimlik: " + maskedId);

        return AuthResponse.builder().token(jwtToken).message("Giriş başarılı.").build();
    }
}