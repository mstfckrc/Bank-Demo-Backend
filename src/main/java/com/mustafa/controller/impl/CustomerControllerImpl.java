package com.mustafa.controller.impl;

import com.mustafa.controller.CustomerController;
import com.mustafa.dto.request.ChangePasswordRequest;
import com.mustafa.dto.request.UpdateProfileRequest;
import com.mustafa.dto.response.UserProfileResponse;
import com.mustafa.service.CustomerService;
import jakarta.validation.Valid;
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

    @GetMapping("/profile")
    @Override
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        return ResponseEntity.ok(customerService.getMyProfile());
    }

    @PutMapping("/profile")
    @Override
    public ResponseEntity<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(customerService.updateProfile(request));
    }

    @PutMapping("/password")
    @Override
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        customerService.changePassword(request);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Şifreniz başarıyla güncellendi.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/appeal")
    @Override
    public ResponseEntity<Map<String, String>> appealRejection() {
        customerService.appealRejection();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Yeniden değerlendirme talebiniz alınmıştır.");
        return ResponseEntity.ok(response);
    }
}