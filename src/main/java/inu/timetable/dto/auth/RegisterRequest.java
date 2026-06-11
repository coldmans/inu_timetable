package inu.timetable.dto.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "아이디를 입력해주세요.")
    @Size(min = 4, max = 30, message = "아이디는 4~30자여야 합니다.")
    String username,

    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(min = 8, max = 100, message = "비밀번호는 8자 이상이어야 합니다.")
    String password,

    @NotNull(message = "학년을 입력해주세요.")
    @Min(value = 1, message = "학년은 1~6 범위여야 합니다.")
    @Max(value = 6, message = "학년은 1~6 범위여야 합니다.")
    Integer grade,

    @NotBlank(message = "전공을 입력해주세요.")
    @Size(max = 100, message = "전공은 100자 이하여야 합니다.")
    String major
) {}
