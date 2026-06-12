package inu.timetable.dto;

public record AdminAuthResponse(
        boolean authenticated,
        String username,
        String csrfToken
) {
}
