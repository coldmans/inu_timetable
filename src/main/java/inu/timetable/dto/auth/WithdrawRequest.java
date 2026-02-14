package inu.timetable.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WithdrawRequest(
    @NotNull(message = "userId는 필수입니다.")
    Long userId,

    @NotBlank(message = "비밀번호를 입력해주세요.")
    String password
) {}
