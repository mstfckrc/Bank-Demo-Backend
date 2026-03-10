package com.mustafa.controller.impl;

import com.mustafa.controller.ICustomerController;
import com.mustafa.dto.request.ChangePasswordRequest;
import com.mustafa.dto.request.UpdateProfileRequest;
import com.mustafa.dto.response.UserProfileResponse;
import com.mustafa.service.ICustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j // 🚀 LOGGER AKTİF
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerControllerImpl implements ICustomerController {

    private final ICustomerService ICustomerService;

    @GetMapping("/profile")
    @Override
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        log.info("REST İsteği: Kullanıcı kendi profil bilgilerini sorguluyor.");
        return ResponseEntity.ok(ICustomerService.getMyProfile());
    }

    @PutMapping("/profile")
    @Override
    public ResponseEntity<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        log.info("REST İsteği: Kullanıcı profil güncelleme talebinde bulundu.");
        return ResponseEntity.ok(ICustomerService.updateProfile(request));
    }

    @PutMapping("/password")
    @Override
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        log.info("REST İsteği: Kullanıcı şifre değiştirme talebinde bulundu.");
        ICustomerService.changePassword(request);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Şifreniz başarıyla güncellendi.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/appeal")
    @Override
    public ResponseEntity<Map<String, String>> appealRejection() {
        log.info("REST İsteği: Reddedilen kullanıcı yeniden değerlendirme (appeal) talebinde bulundu.");
        ICustomerService.appealRejection();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Yeniden değerlendirme talebiniz alınmıştır.");
        return ResponseEntity.ok(response);
    }
}