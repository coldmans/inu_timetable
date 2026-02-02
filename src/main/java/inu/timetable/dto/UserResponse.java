package inu.timetable.dto;

import inu.timetable.entity.User;

public record UserResponse(
    Long id,
    String username,
    String nickname,
    Integer grade,
    String major
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getNickname(),
            user.getGrade(),
            user.getMajor()
        );
    }
}