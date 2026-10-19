package com.survive_the_traffic.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(

    @NotBlank
    @Email
    String email,

    @NotBlank
    @Size(min=8, max=12)
    String password,

    @NotBlank
    @Size(max = 50)
    String name
) {

}
