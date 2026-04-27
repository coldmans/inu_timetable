package inu.timetable.service;

import inu.timetable.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentMappingServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    private DepartmentMappingService departmentMappingService;

    @BeforeEach
    void setUp() {
        departmentMappingService = new DepartmentMappingService(subjectRepository);
        // DB에서 학과 목록을 가져오는 부분은 빈 리스트로 모킹 (수동 매핑만 테스트)
        when(subjectRepository.findDistinctDepartments()).thenReturn(Collections.emptyList());
        departmentMappingService.initializeMappings();
    }

    @Test
    void testManualMappings() {
        // 기존 매핑 확인
        assertThat(departmentMappingService.getFullName("독문")).isEqualTo("독어독문학과");

        // 새로 추가한 매핑 확인
        assertThat(departmentMappingService.getFullName("임베")).isEqualTo("임베디드시스템공학과");
        assertThat(departmentMappingService.getFullName("임베디드")).isEqualTo("임베디드시스템공학과");
        assertThat(departmentMappingService.getFullName("산경")).isEqualTo("산업경영공학과");
        assertThat(departmentMappingService.getFullName("컴퓨터")).isEqualTo("컴퓨터공학부");
        assertThat(departmentMappingService.getFullName("해양학")).isEqualTo("해양학과");
    }

    @Test
    void testParseAbbreviations() {
        // 복합 케이스 확인
        List<String> results = departmentMappingService.parseAbbreviations("임베디드, 산경");
        assertThat(results).containsExactly("임베디드시스템공학과", "산업경영공학과");

        // 단일 케이스 확인
        List<String> singleResult = departmentMappingService.parseAbbreviations("컴퓨터");
        assertThat(singleResult).containsExactly("컴퓨터공학부");
    }
}
