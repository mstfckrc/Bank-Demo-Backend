package com.mustafa.service;

import com.mustafa.dto.request.ChangePasswordRequest;
import com.mustafa.dto.request.UpdateProfileRequest;
import com.mustafa.dto.response.CustomerResponse; // Eğer bu DTO yoksa String dönebiliriz
import com.mustafa.dto.response.UserProfileResponse;
import org.springframework.transaction.annotation.Transactional;

public interface CustomerService {

    CustomerResponse updateProfile(UpdateProfileRequest request);

    void changePassword(ChangePasswordRequest request);

    UserProfileResponse getMyProfile();

    void appealRejection();
}