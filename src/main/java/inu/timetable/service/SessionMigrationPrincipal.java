package inu.timetable.service;

public record SessionMigrationPrincipal(
        PrincipalType type,
        Long userId,
        String adminUsername) {

    public static SessionMigrationPrincipal user(Long userId) {
        return new SessionMigrationPrincipal(PrincipalType.USER, userId, null);
    }

    public static SessionMigrationPrincipal admin(String username) {
        return new SessionMigrationPrincipal(PrincipalType.ADMIN, null, username);
    }

    public enum PrincipalType {
        USER,
        ADMIN
    }
}
