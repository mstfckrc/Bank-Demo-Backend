package com.mustafa.dto.request;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String email;
    private String fullName; // Sadece bu ikisini güncelleyebilsin
}