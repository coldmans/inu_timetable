package inu.timetable.dto;

import inu.timetable.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectFilterCriteriaTest {

    @Test
    void ofNormalizesTimeBlocksToDayOrderForStableCacheKey() {
        SubjectFilterCriteria criteria = criteriaWithTimeBlocks(
                Arrays.asList(" 금:4-9 ", "수:4-10", null, " "));

        assertThat(criteria.timeBlocks()).containsExactly("수:4-10", "금:4-9");

        SubjectFilterCriteria sameCriteria = criteriaWithTimeBlocks(List.of("수:4-10", "금:4-9"));
        assertThat(criteria).isEqualTo(sameCriteria);
    }

    @Test
    void ofNormalizesMissingTimeBlocksToEmptyList() {
        assertThat(criteriaWithTimeBlocks(null).timeBlocks()).isEmpty();
        assertThat(criteriaWithTimeBlocks(List.of()).timeBlocks()).isEmpty();
    }

    @Test
    void ofRejectsMalformedTimeBlocks() {
        for (String invalid : List.of("월:abc", "일:1-2", "수:4~10", "수4-10", "수:9-4")) {
            assertThatThrownBy(() -> criteriaWithTimeBlocks(List.of(invalid)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void ofRejectsDuplicateDaysInTimeBlocks() {
        assertThatThrownBy(() -> criteriaWithTimeBlocks(List.of("수:1-3", "수:4-10")))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void toTimeBlockParamsExpandsBlocksIntoPerDayRanges() {
        SubjectFilterCriteria criteria = criteriaWithTimeBlocks(List.of("수:4-10", "금:4.5-9"));

        SubjectFilterCriteria.TimeBlockParams params = criteria.toTimeBlockParams();

        assertThat(params.active()).isTrue();
        assertThat(params.wedStart()).isEqualTo(4.0);
        assertThat(params.wedEnd()).isEqualTo(10.0);
        assertThat(params.friStart()).isEqualTo(4.5);
        assertThat(params.friEnd()).isEqualTo(9.0);
        assertThat(params.monStart()).isNull();
        assertThat(params.tueStart()).isNull();
        assertThat(params.thuStart()).isNull();
        assertThat(params.satStart()).isNull();
    }

    @Test
    void toTimeBlockParamsIsInactiveWithoutTimeBlocks() {
        SubjectFilterCriteria.TimeBlockParams params = criteriaWithTimeBlocks(null).toTimeBlockParams();

        assertThat(params.active()).isFalse();
        assertThat(params.monStart()).isNull();
        assertThat(params.satEnd()).isNull();
    }

    private SubjectFilterCriteria criteriaWithTimeBlocks(List<String> timeBlocks) {
        return SubjectFilterCriteria.of(
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                timeBlocks, 0, 20);
    }
}
