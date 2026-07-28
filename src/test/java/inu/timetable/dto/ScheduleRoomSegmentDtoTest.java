package inu.timetable.dto;

import inu.timetable.entity.Schedule;
import inu.timetable.entity.ScheduleRoomSegment;
import inu.timetable.entity.Subject;
import inu.timetable.entity.WishlistItem;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleRoomSegmentDtoTest {

    @Test
    void publicSubjectDtoExposesRoomSegmentsWithClockTimes() {
        Subject subject = subjectWithRoomSegment();

        SubjectDto dto = SubjectDto.from(subject);

        assertThat(dto.getSchedules().get(0).getRoomSegments())
                .singleElement()
                .satisfies(segment -> {
                    assertThat(segment.getId()).isEqualTo(20L);
                    assertThat(segment.getRoom()).isEqualTo("05-432");
                    assertThat(segment.getStartTime()).isEqualTo("09:00");
                    assertThat(segment.getEndTime()).isEqualTo("10:30");
                });
    }

    @Test
    void wishlistDtoExposesRoomSegmentsWithClockTimes() {
        Subject subject = subjectWithRoomSegment();
        WishlistItem wishlistItem = WishlistItem.builder()
                .id(30L)
                .subject(subject)
                .semester("2026-2")
                .priority(1)
                .isRequired(false)
                .build();

        WishlistItemDto dto = WishlistItemDto.fromEntity(wishlistItem);

        assertThat(dto.getSchedules().get(0).getRoomSegments())
                .singleElement()
                .satisfies(segment -> {
                    assertThat(segment.getRoom()).isEqualTo("05-432");
                    assertThat(segment.getStartTime()).isEqualTo("09:00");
                    assertThat(segment.getEndTime()).isEqualTo("10:30");
                });
    }

    @Test
    void managementResponseExposesNumericRoomSegmentRange() {
        Subject subject = subjectWithRoomSegment();

        SubjectManagementResponse response = SubjectManagementResponse.from(subject);

        assertThat(response.getSchedules().get(0).getRoomSegments())
                .singleElement()
                .satisfies(segment -> {
                    assertThat(segment.getRoom()).isEqualTo("05-432");
                    assertThat(segment.getStartTime()).isEqualTo(1.0);
                    assertThat(segment.getEndTime()).isEqualTo(2.5);
                });
    }

    private Subject subjectWithRoomSegment() {
        Subject subject = Subject.builder()
                .id(1L)
                .courseCode("CSE0001001")
                .semester("2026-2")
                .active(true)
                .subjectName("자료구조")
                .credits(3)
                .professor("김교수")
                .department("컴퓨터공학부")
                .grade(2)
                .subjectType(SubjectType.전심)
                .classMethod(ClassMethod.OFFLINE)
                .isNight(false)
                .schedules(new ArrayList<>())
                .build();
        Schedule schedule = Schedule.builder()
                .id(10L)
                .subject(subject)
                .dayOfWeek("월")
                .startTime(1.0)
                .endTime(3.0)
                .build();
        schedule.getRoomSegments().add(ScheduleRoomSegment.builder()
                .id(20L)
                .schedule(schedule)
                .room("05-432")
                .startTime(1.0)
                .endTime(2.5)
                .build());
        subject.getSchedules().add(schedule);
        return subject;
    }
}
