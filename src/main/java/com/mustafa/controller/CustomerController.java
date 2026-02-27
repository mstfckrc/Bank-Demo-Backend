package com.mustafa.controller;

import com.mustafa.dto.request.ChangePasswordRequest;
import com.mustafa.dto.request.UpdateProfileRequest;
import com.mustafa.dto.response.CustomerResponse;
import com.mustafa.dto.response.UserProfileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

public interface CustomerController {

    ResponseEntity<CustomerResponse> updateProfile(@RequestBody UpdateProfileRequest request);

    ResponseEntity<Map<String, String>> changePassword(@RequestBody ChangePasswordRequest request);

    ResponseEntity<UserProfileResponse> getMyProfile();

    // 🚀 YENİ: Yeniden değerlendirme talep endpoint'i
    @PostMapping("/appeal")
    ResponseEntity<Map<String, String>> appealRejection();
}