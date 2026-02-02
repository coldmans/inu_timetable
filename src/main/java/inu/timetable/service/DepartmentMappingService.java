package inu.timetable.service;

import inu.timetable.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * 학과 줄임말-정식명칭 매핑 서비스
 * 기초교양 PDF에서 "독문,불문,중국" 같은 줄임말을 실제 학과명으로 변환
 */
@Service
@RequiredArgsConstructor
public class DepartmentMappingService {

    private final SubjectRepository subjectRepository;
    private final Map<String, String> abbreviationToFullName = new HashMap<>();
    private final Map<String, String> fullNameToAbbreviation = new HashMap<>();

    /**
     * 애플리케이션 시작 시 DB에서 학과 목록을 가져와 매핑 테이블 초기화
     */
    @PostConstruct
    public void initializeMappings() {
        System.out.println("=== 학과 매핑 테이블 초기화 시작 ===");

        // DB에서 모든 고유한 학과명 가져오기
        List<String> departments = subjectRepository.findDistinctDepartments();
        System.out.println("DB에서 가져온 학과 수: " + departments.size());

        // 수동 매핑 추가 (일반적인 줄임말)
        addManualMappings();

        // DB 학과명 기반 자동 매핑 생성
        for (String department : departments) {
            if (department == null || department.isBlank() || department.equals("미분류")) {
                continue;
            }

            String abbreviation = generateAbbreviation(department);
            if (!abbreviationToFullName.containsKey(abbreviation)) {
                abbreviationToFullName.put(abbreviation, department);
                fullNameToAbbreviation.put(department, abbreviation);
            }
        }

        System.out.println("매핑 테이블 크기: " + abbreviationToFullName.size());
        System.out.println("=== 학과 매핑 테이블 초기화 완료 ===");
    }

    /**
     * 수동 매핑 추가 (자주 사용되는 줄임말)
     */
    private void addManualMappings() {
        // 인문대학
        addMapping("국문", "국어국문학과");
        addMapping("영문", "영어영문학과");
        addMapping("독문", "독어독문학과");
        addMapping("불문", "불어불문학과");
        addMapping("일문", "일어일문학과");
        addMapping("중국", "중국학과");
        addMapping("일본지역문화", "일본지역문화학과");

        // 사회과학대학
        addMapping("행정", "행정학과");
        addMapping("정외", "정치외교학과");
        addMapping("경제", "경제학과");
        addMapping("무역", "무역학부");
        addMapping("소비자", "소비자학과");

        // 법과대학
        addMapping("법학부", "법학부");

        // 사범대학
        addMapping("국어교육", "국어교육과");
        addMapping("영어교육", "영어교육과");
        addMapping("일어교육", "일어교육과");
        addMapping("수학교육", "수학교육과");
        addMapping("체육교육", "체육교육과");
        addMapping("유아교육", "유아교육과");
        addMapping("역사교육", "역사교육과");
        addMapping("윤리교육", "윤리교육과");
        addMapping("사회복지", "사회복지학과");

        // 자연과학대학
        addMapping("수학", "수학과");
        addMapping("물리", "물리학과");
        addMapping("화학", "화학과");
        addMapping("패션", "패션산업학과");
        addMapping("해양", "해양학과");
        addMapping("해양학", "해양학과");

        // 공과대학
        addMapping("기계", "기계공학과");
        addMapping("전기", "전기공학과");
        addMapping("전자", "전자공학과");
        addMapping("산공", "산업경영공학과");
        addMapping("산경", "산업경영공학과");
        addMapping("신소재", "신소재공학과");
        addMapping("안전", "안전공학과");
        addMapping("에너지화학", "에너지화학공학과");

        // 정보기술대학
        addMapping("컴공", "컴퓨터공학부");
        addMapping("컴퓨터", "컴퓨터공학부");
        addMapping("임베", "임베디드시스템공학과");
        addMapping("임베디드", "임베디드시스템공학과");
        addMapping("정보통신", "정보통신공학과");

        // 경영대학
        addMapping("경영", "경영학부");
        addMapping("세무", "세무회계학과");

        // 예술체육대학
        addMapping("조형예술", "조형예술학부");
        addMapping("디자인", "디자인학부");
        addMapping("공연예술", "공연예술학과");
        addMapping("스포츠과학", "스포츠과학부");
        addMapping("운동건강", "운동건강학부");

        // 도시과학대학
        addMapping("도시행정", "도시행정학과");
        addMapping("도시공학", "도시공학과");
        addMapping("도시건축", "도시건축학부");

        // 동북아국제통상학부
        addMapping("동북아", "동북아국제통상학부");

        // 기타
        addMapping("문헌정보", "문헌정보학과");
        addMapping("미디어커뮤니케이션", "미디어커뮤니케이션학과");
        addMapping("창의인재개발", "창의인재개발학과");
    }

    private void addMapping(String abbreviation, String fullName) {
        abbreviationToFullName.put(abbreviation, fullName);
        fullNameToAbbreviation.put(fullName, abbreviation);
    }

    /**
     * 학과명에서 줄임말 자동 생성
     * 예: "독어독문학과" -> "독문"
     */
    private String generateAbbreviation(String department) {
        // "학과", "학부" 제거
        String abbr = department.replace("학과", "").replace("학부", "");

        // 특정 패턴 처리
        if (abbr.contains("어") && abbr.contains("문")) {
            // "독어독문" -> "독문"
            int index = abbr.indexOf("문");
            if (index > 1) {
                abbr = abbr.substring(0, 2) + "문";
            }
        }

        return abbr;
    }

    /**
     * 줄임말을 정식 학과명으로 변환
     */
    public String getFullName(String abbreviation) {
        return abbreviationToFullName.getOrDefault(abbreviation.trim(), abbreviation);
    }

    /**
     * 정식 학과명을 줄임말로 변환
     */
    public String getAbbreviation(String fullName) {
        return fullNameToAbbreviation.getOrDefault(fullName.trim(), fullName);
    }

    /**
     * 쉼표로 구분된 줄임말 목록을 정식 학과명 목록으로 변환
     * 예: "독문,불문,중국" -> ["독어독문학과", "불어불문학과", "중국학과"]
     */
    public List<String> parseAbbreviations(String abbreviationsString) {
        if (abbreviationsString == null || abbreviationsString.isBlank()) {
            return Collections.emptyList();
        }

        List<String> fullNames = new ArrayList<>();
        String[] abbrs = abbreviationsString.split("[,，\\s]+"); // 쉼표, 전각쉼표, 공백으로 분리

        for (String abbr : abbrs) {
            String trimmed = abbr.trim();
            if (!trimmed.isEmpty()) {
                String fullName = getFullName(trimmed);
                fullNames.add(fullName);
            }
        }

        return fullNames;
    }

    /**
     * 매핑 테이블 디버그 출력
     */
    public void printMappings() {
        System.out.println("\n=== 학과 매핑 테이블 ===");
        abbreviationToFullName.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> System.out.println(entry.getKey() + " -> " + entry.getValue()));
        System.out.println("========================\n");
    }
}
