package com.mustafa.service.impl;

import com.mustafa.dto.request.LoginRequest;
import com.mustafa.dto.request.RegisterRequest;
import com.mustafa.dto.response.AuthResponse;
import com.mustafa.entity.Customer;
import com.mustafa.repository.CustomerRepository;
import com.mustafa.security.JwtService;
import com.mustafa.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    public AuthResponse register(RegisterRequest request) {
        // 1. KONTROL: Bu TC No veya Email daha önce kullanılmış mı?
        // AuthServiceImpl.java içi
        if (customerRepository.existsByTcNo(request.getTcNo())) {
            throw new com.mustafa.exception.CustomerAlreadyExistsException("Bu TC Kimlik Numarası ile zaten kayıtlı bir müşteri var!");
        }
        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new com.mustafa.exception.CustomerAlreadyExistsException("Bu Email adresi zaten kullanımda!");
        }

        // 2. Müşteriyi oluştur ve şifresini güvenli hale getir (Bcrypt)
        Customer customer = Customer.builder()
                .tcNo(request.getTcNo())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // Şifre çırpıldı!
                .role(Customer.Role.USER) // Yeni kayıt olan herkes standart kullanıcıdır
                .status(Customer.ApprovalStatus.PENDING) // 🚀 YENİ EKLENDİ: Artık herkes beklemede başlıyor!
                .build();

        // 3. Veritabanına kaydet
        customerRepository.save(customer);

        // 4. React tarafına göndermek için taptaze bir JWT Token üret
        String jwtToken = jwtService.generateToken(customer);

        // 5. Cevabı dön
        return AuthResponse.builder()
                .token(jwtToken)
                .message("Kayıt işlemi başarıyla gerçekleşti.")
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // 1. Spring Security, TC No ve Şifreyi arka planda karşılaştırır
        // Eğer şifre yanlışsa burada otomatik olarak exception fırlar ve kod aşağıya inmez
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getTcNo(),
                        request.getPassword()
                )
        );

        // 2. Şifre doğruysa (kod buraya indiyse), müşteriyi veritabanından çek
        Customer customer = customerRepository.findByTcNo(request.getTcNo())
                .orElseThrow(() -> new RuntimeException("Müşteri bulunamadı"));

        // 3. Sisteme giren bu müşteriye yeni bir Token üret
        String jwtToken = jwtService.generateToken(customer);

        // 4. Cevabı dön
        return AuthResponse.builder()
                .token(jwtToken)
                .message("Giriş başarılı.")
                .build();
    }
}