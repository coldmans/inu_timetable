package inu.timetable.dto;

import inu.timetable.entity.ScheduleRoomSegment;
import inu.timetable.util.TimeConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleRoomSegmentDto {

    private Long id;
    private String room;
    private String startTime;
    private String endTime;

    public static ScheduleRoomSegmentDto fromEntity(ScheduleRoomSegment segment) {
        return ScheduleRoomSegmentDto.builder()
                .id(segment.getId())
                .room(segment.getRoom())
                .startTime(TimeConverter.convertToClockTime(segment.getStartTime()))
                .endTime(TimeConverter.convertToClockTime(segment.getEndTime()))
                .build();
    }
}
