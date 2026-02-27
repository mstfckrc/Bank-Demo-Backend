package com.mustafa.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

    @NotBlank(message = "TC Kimlik No boş bırakılamaz")
    @Size(min = 11, max = 11, message = "TC Kimlik No 11 haneli olmalıdır")
    private String tcNo;

    @NotBlank(message = "Şifre boş bırakılamaz")
    private String password;
}