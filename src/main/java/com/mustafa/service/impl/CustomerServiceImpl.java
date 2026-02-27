package com.mustafa.service.impl;

import com.mustafa.dto.request.ChangePasswordRequest;
import com.mustafa.dto.request.UpdateProfileRequest;
import com.mustafa.dto.response.CustomerResponse;
import com.mustafa.dto.response.UserProfileResponse;
import com.mustafa.entity.Customer;
import com.mustafa.exception.BankOperationException;
import com.mustafa.repository.CustomerRepository;
import com.mustafa.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public CustomerResponse updateProfile(UpdateProfileRequest request) {
        String currentTcNo = SecurityContextHolder.getContext().getAuthentication().getName();

        Customer customer = customerRepository.findByTcNo(currentTcNo)
                .orElseThrow(() -> new BankOperationException("Müşteri bulunamadı!"));

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            customer.setEmail(request.getEmail());
        }

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            // PROFIL ICIN EXCEPTION KONTROLU
            if (request.getFullName().length() < 3) {
                throw new BankOperationException("Ad soyad en az 3 karakter olmalıdır!");
            }
            customer.setFullName(request.getFullName());
        }

        customerRepository.save(customer);

        return CustomerResponse.builder()
                .tcNo(customer.getTcNo())
                .fullName(customer.getFullName())
                .email(customer.getEmail())
                .role(customer.getRole().name()) // EKLENDİ
                .status(customer.getStatus().name()) // 🚀 ŞU SATIRI TAM BURAYA EKLE
                .build();
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        String currentTcNo = SecurityContextHolder.getContext().getAuthentication().getName();

        Customer customer = customerRepository.findByTcNo(currentTcNo)
                .orElseThrow(() -> new BankOperationException("Müşteri bulunamadı!"));

        // SIFRE ICIN EXCEPTION KONTROLLERI
        if (!passwordEncoder.matches(request.getOldPassword(), customer.getPassword())) {
            throw new BankOperationException("Eski şifreniz hatalı!");
        }

        if (request.getNewPassword().length() < 6) {
            throw new BankOperationException("Yeni şifreniz en az 6 karakter olmalıdır ve güvenli olmalıdır!");
        }

        customer.setPassword(passwordEncoder.encode(request.getNewPassword()));
        customerRepository.save(customer);
    }

    public UserProfileResponse getMyProfile() {
        // 1. Token'dan giriş yapmış kullanıcının tcNo'sunu al (Spring Security bunu otomatik yapar)
        String tcNo = SecurityContextHolder.getContext().getAuthentication().getName();

        // 2. Veritabanından müşteriyi bul
        Customer customer = customerRepository.findByTcNo(tcNo)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));

        // 3. Bilgileri DTO'ya çevirip dön
        return UserProfileResponse.builder()
                .tcNo(customer.getTcNo())
                .fullName(customer.getFullName())
                .email(customer.getEmail())
                .role(customer.getRole().name()) // Enum ise .name() yapıyoruz
                .status(customer.getStatus().name()) // 🚀 ŞU SATIRI TAM BURAYA EKLE
                .build();
    }

    @Transactional
    @Override
    public void appealRejection() {
        // 1. İsteği atan müşteriyi bul
        String currentTcNo = SecurityContextHolder.getContext().getAuthentication().getName();
        Customer customer = customerRepository.findByTcNo(currentTcNo)
                .orElseThrow(() -> new BankOperationException("Müşteri bulunamadı!"));

        // 2. Sadece REDDEDİLMİŞ kişiler bu talebi yapabilir
        if (customer.getStatus() != Customer.ApprovalStatus.REJECTED) {
            throw new BankOperationException("Sadece reddedilen hesaplar yeniden değerlendirme talebinde bulunabilir.");
        }

        // 3. Müşteriyi tekrar admin onayına (PENDING) gönder
        customer.setStatus(Customer.ApprovalStatus.PENDING);
        customerRepository.save(customer);
    }
}