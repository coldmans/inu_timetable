package inu.timetable.dto;

import inu.timetable.entity.User;
import inu.timetable.entity.UserMajor;
import inu.timetable.enums.UserMajorType;

import java.util.Comparator;
import java.util.List;

public record UserResponse(
    Long id,
    String username,
    String nickname,
    Integer grade,
    String major,
    List<UserMajorResponse> majors
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getNickname(),
            user.getGrade(),
            user.getMajor(),
            user.getUserMajors().stream()
                    .sorted(Comparator
                            .comparing((UserMajor userMajor) -> majorSortOrder(userMajor.getType()))
                            .thenComparing(UserMajor::getDepartment))
                    .map(UserMajorResponse::from)
                    .toList()
        );
    }

    private static int majorSortOrder(UserMajorType type) {
        return switch (type) {
            case PRIMARY -> 0;
            case DOUBLE -> 1;
            case MINOR -> 2;
        };
    }

    public record UserMajorResponse(
            String type,
            String label,
            String department
    ) {
        public static UserMajorResponse from(UserMajor userMajor) {
            return new UserMajorResponse(
                    userMajor.getType().name(),
                    majorLabel(userMajor.getType()),
                    userMajor.getDepartment()
            );
        }

        private static String majorLabel(UserMajorType type) {
            return switch (type) {
                case PRIMARY -> "전공";
                case DOUBLE -> "복수전공";
                case MINOR -> "부전공";
            };
        }
    }
}
