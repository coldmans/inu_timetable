package inu.timetable.service;

public record SessionMigrationPrincipal(
        PrincipalType type,
        Long userId,
        String adminUsername,
        Long adminCredentialVersion) {

    public static SessionMigrationPrincipal user(Long userId) {
        return new SessionMigrationPrincipal(PrincipalType.USER, userId, null, null);
    }

    public static SessionMigrationPrincipal admin(String username, Long credentialVersion) {
        return new SessionMigrationPrincipal(
                PrincipalType.ADMIN,
                null,
                username,
                credentialVersion);
    }

    public enum PrincipalType {
        USER,
        ADMIN
    }
}
