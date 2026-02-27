package com.mustafa.controller.impl;

import com.mustafa.controller.CustomerController;
import com.mustafa.dto.request.ChangePasswordRequest;
import com.mustafa.dto.request.UpdateProfileRequest;
import com.mustafa.dto.response.CustomerResponse;
import com.mustafa.dto.response.UserProfileResponse;
import com.mustafa.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerControllerImpl implements CustomerController {

    private final CustomerService customerService;

    @Override
    @PutMapping("/profile")
    public ResponseEntity<CustomerResponse> updateProfile(@RequestBody UpdateProfileRequest request) {
        // Profil güncellenince zaten CustomerResponse DTO'su (JSON) dönecek
        return ResponseEntity.ok(customerService.updateProfile(request));
    }

    @Override
    @PutMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(@RequestBody ChangePasswordRequest request) {
        customerService.changePassword(request);

        // Başarılı durumu düz String yerine JSON objesi olarak dönüyoruz
        Map<String, String> response = new HashMap<>();
        response.put("message", "Şifreniz başarıyla güncellenmiştir.");

        return ResponseEntity.ok(response);
    }

    // GET /api/v1/customers/profile
    @Override
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        return ResponseEntity.ok(customerService.getMyProfile());
    }

    // 🚀 YENİ: Yeniden değerlendirme talep endpoint'i
    @PostMapping("/appeal")
    @Override
    public ResponseEntity<Map<String, String>> appealRejection() {
        customerService.appealRejection();

        Map<String, String> response = new HashMap<>();
        response.put("message", "Yeniden değerlendirme talebiniz başarıyla alınmıştır. Hesabınız şu an yönetici onayındadır.");

        return ResponseEntity.ok(response);
    }
}