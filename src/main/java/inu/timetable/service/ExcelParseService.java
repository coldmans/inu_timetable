package inu.timetable.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelParseService {

    private final SubjectRepository subjectRepository;
    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public int parseAndSaveSubjects(MultipartFile file) throws IOException {
        System.out.println("=== Excel 파싱 시작 (AI 기반) ===");

        // 1단계: Excel을 텍스트로 변환
        String excelText = extractTextFromExcel(file);
        System.out.println("Excel 텍스트 길이: " + excelText.length() + " 문자");

        // 2단계: Gemini AI로 파싱
        List<Subject> allSubjects = parseWithGemini(excelText);
        System.out.println("AI 파싱 완료: " + allSubjects.size() + "개 과목 추출");

        // 3단계: 데이터베이스 저장
        System.out.println("\n=== 데이터베이스 저장 시작 ===");
        if (!allSubjects.isEmpty()) {
            try {
                List<Subject> savedSubjects = subjectRepository.saveAll(allSubjects);
                System.out.println("성공적으로 저장된 과목 수: " + savedSubjects.size());
            } catch (Exception e) {
                System.err.println("데이터베이스 저장 오류: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
        } else {
            System.out.println("저장할 과목이 없습니다.");
        }

        return allSubjects.size();
    }

    /**
     * Excel 파일을 텍스트로 변환
     */
    private String extractTextFromExcel(MultipartFile file) throws IOException {
        StringBuilder text = new StringBuilder();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            System.out.println("Sheet 이름: " + sheet.getSheetName());
            System.out.println("전체 행 수: " + sheet.getPhysicalNumberOfRows());

            // 헤더 행 추가
            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                text.append("=== 헤더 ===\n");
                for (Cell cell : headerRow) {
                    String value = getCellValueAsString(cell);
                    if (value != null) {
                        text.append(value).append("\t");
                    }
                }
                text.append("\n\n");
            }

            // 데이터 행 추가 (헤더 제외, 최대 100개 행만 처리 - Gemini 토큰 제한)
            text.append("=== 데이터 ===\n");
            int maxRows = Math.min(sheet.getLastRowNum(), 100);
            for (int rowIndex = 1; rowIndex <= maxRows; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                // 각 셀의 값을 탭으로 구분하여 추가
                for (int colIndex = 0; colIndex < 17; colIndex++) { // 17개 컬럼
                    Cell cell = row.getCell(colIndex);
                    String value = getCellValueAsString(cell);
                    text.append(value != null ? value : "").append("\t");
                }
                text.append("\n");
            }
        }

        return text.toString();
    }

    /**
     * Gemini AI로 Excel 텍스트 파싱
     */
    private List<Subject> parseWithGemini(String excelText) {
        try {
            String prompt =
                """
                다음은 대학교 종합강의시간표 Excel 데이터입니다. 이를 정확하게 JSON 형식으로 파싱해주세요.

                **Excel 컬럼 구조:**
                순번, 대학(원), 학과(부), 학년, 이수구분, 이수영역, 학수번호, 교과목명, 교과목명(영문), 담당교수, 강의실, 시간표(교시), 학점, 수업구분, 수업유형, 성적평가, 원어강의

                **중요한 파싱 규칙:**
                1. 시간표(교시) 형식이 매우 복잡합니다:
                   - [15-116:화(7)] [15-317:월(3)] → 여러 강의실, 각각 다른 요일/시간
                   - [15-118B:월(3),화(7)] → 하나의 강의실, 여러 요일 (쉼표로 구분)
                   - [15-116:화(5B-6),금(5B-6)] → 여러 요일, 각각 시간 범위
                   - [15-403:월(7)(8)(9)] → 연속 교시 (괄호만 사용)

                2. 강의실 정보는 무시하고, 요일과 교시만 추출하여 timeString에 저장
                   예: [15-116:화(7)] [15-317:월(3)] → "화 7 월 3"
                   예: [15-403:월(7)(8)(9)] → "월 7-9"

                3. 학년이 "전학년"이면 null로 설정

                4. 빈 값 처리: 담당교수가 비어있으면 null로 설정

                5. 야간수업: "야" 키워드가 있으면 isNight를 true로 설정

                6. 이수구분: 전심(전공심화), 전핵(전공핵심), 기교(기초교양), 전기(전공기초), 일선(일반선택), 심교(심화교양), 핵교(핵심교양)

                7. 수업유형(classMethod):
                   - "e-Learning", "온라인", "비대면" → ONLINE
                   - "블렌디드", "혼합" → BLENDED
                   - 나머지 → OFFLINE

                각 과목에 대해 다음 정보를 추출해주세요:
                - subjectName: 교과목명 (필수, 문자열)
                - credits: 학점 (필수, 숫자)
                - professor: 담당교수 (문자열, 비어있으면 null)
                - timeString: 요일 및 교시 원본 문자열 (예: "월 4-5A 목 4-5A", "화 7-9")
                - isNight: 야간 수업 여부 (true/false)
                - subjectType: 이수구분 (전심, 전핵, 심교, 핵교, 일선, 기교, 전기 중 하나)
                - classMethod: 수업방법 (ONLINE, OFFLINE, BLENDED 중 하나)
                - grade: 학년 (1-4 중 하나, "전학년"이면 null)
                - department: 학과명

                응답은 다음과 같은 JSON 배열 형태로만 주세요 (JSON 외에 다른 설명 금지):
                [
                  {
                    "subjectName": "영작문(1)",
                    "credits": 1,
                    "professor": "알라나 커밍스",
                    "timeString": "화 7 월 3",
                    "isNight": false,
                    "subjectType": "전핵",
                    "classMethod": "OFFLINE",
                    "grade": 1,
                    "department": "영어영문학과"
                  }
                ]

                Excel 데이터:
                """
                    + excelText;

            String requestBody =
                """
                {
                  "contents": [{"parts": [{"text": "%s"}]}],
                  "generationConfig": {
                    "temperature": 0.1, "topK": 16, "topP": 0.95, "maxOutputTokens": 65536,
                    "responseMimeType": "application/json"
                  },
                  "safetySettings": [
                    {"category": "HARM_CATEGORY_HARASSMENT", "threshold": "BLOCK_NONE"},
                    {"category": "HARM_CATEGORY_HATE_SPEECH", "threshold": "BLOCK_NONE"},
                    {"category": "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold": "BLOCK_NONE"},
                    {"category": "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold": "BLOCK_NONE"}
                  ]
                }
                """
                    .formatted(prompt.replace("\"", "\\\"").replace("\n", "\\n"));

            String response =
                webClient
                    .post()
                    .uri(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent")
                    .header("Content-Type", "application/json")
                    .header("X-goog-api-key", geminiApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseGeminiResponse(response);

        } catch (Exception e) {
            System.err.println("Gemini API 호출 실패: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Gemini 응답 파싱
     */
    private List<Subject> parseGeminiResponse(String response) {
        List<Subject> subjects = new ArrayList<>();
        try {
            JsonNode jsonResponse = objectMapper.readTree(response);
            JsonNode candidates = jsonResponse.path("candidates");
            if (candidates.isMissingNode() || !candidates.isArray() || candidates.isEmpty()) {
                System.err.println("Gemini 응답에서 'candidates'를 찾을 수 없습니다.");
                return subjects;
            }
            String content = candidates.get(0).path("content").path("parts").get(0).path("text").asText();

            System.out.println("JSON 길이: " + content.length());

            String trimmedContent = content.trim();
            if (trimmedContent.startsWith("[") && trimmedContent.endsWith("]")) {
                com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>> typeRef
                  = new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {};
                java.util.List<java.util.Map<String, Object>> subjectMaps = objectMapper.readValue(content, typeRef);

                System.out.println("파싱 성공 - 과목 수: " + subjectMaps.size());

                for (java.util.Map<String, Object> subjectMap : subjectMaps) {
                    JsonNode subjectNode = objectMapper.valueToTree(subjectMap);
                    Subject subject = parseSubjectFromJson(subjectNode);
                    if (subject != null) {
                        subjects.add(subject);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Gemini 응답 파싱 실패: " + e.getMessage());
            e.printStackTrace();
        }
        return subjects;
    }

    /**
     * JSON 노드를 Subject 엔티티로 변환
     * PdfParseService의 메서드들을 재사용
     */
    private Subject parseSubjectFromJson(JsonNode node) {
        try {
            String subjectName = getStringValue(node, "subjectName");
            Integer credits = getIntValue(node, "credits");

            if (subjectName == null || credits == null) {
                System.err.println("필수 필드 누락: " + node.toString());
                return null;
            }

            String timeString = getStringValue(node, "timeString");
            // PdfParseService의 parseTime 메서드를 재사용
            List<Schedule> schedules = parseTimeString(timeString);

            Subject subject =
                Subject.builder()
                    .subjectName(subjectName)
                    .credits(credits)
                    .professor(getStringValue(node, "professor", "미배정"))
                    .isNight(getBooleanValue(node, "isNight", false))
                    .subjectType(parseSubjectType(getStringValue(node, "subjectType")))
                    .classMethod(parseClassMethod(getStringValue(node, "classMethod")))
                    .grade(getIntValue(node, "grade"))
                    .department(getStringValue(node, "department", "미분류"))
                    .schedules(new ArrayList<>())
                    .build();

            for (Schedule schedule : schedules) {
                schedule.setSubject(subject);
                subject.getSchedules().add(schedule);
            }
            return subject;
        } catch (Exception e) {
            System.err.println("Subject JSON 파싱 오류: " + e.getMessage());
            return null;
        }
    }

    /**
     * 시간표 문자열 파싱
     * Gemini가 반환한 간단한 형식을 파싱: "월 4-5A 목 4-5A"
     */
    private List<Schedule> parseTimeString(String timeString) {
        // PdfParseService 활용할 수 없으면 간단한 파싱만 수행
        // 실제로는 PdfParseService의 parseTime을 재사용하는 것이 좋음
        List<Schedule> schedules = new ArrayList<>();
        if (timeString == null || timeString.isBlank()) {
            return schedules;
        }

        // 간단한 파싱: "월 7 화 3" 또는 "월 7-9"
        String[] parts = timeString.split("\\s+");
        for (int i = 0; i < parts.length - 1; i += 2) {
            String day = parts[i];
            if (day.length() == 1 && "월화수목금토일".contains(day)) {
                try {
                    String timePart = parts[i + 1];
                    double start, end;

                    if (timePart.contains("-")) {
                        String[] range = timePart.split("-");
                        start = parseTimeValue(range[0]);
                        end = parseTimeValue(range[1]) + 1.0;
                    } else {
                        start = parseTimeValue(timePart);
                        end = start + 1.0;
                    }

                    schedules.add(
                        Schedule.builder()
                            .dayOfWeek(day)
                            .startTime(start)
                            .endTime(end)
                            .build()
                    );
                } catch (Exception e) {
                    System.err.println("시간 파싱 오류: " + parts[i + 1]);
                }
            }
        }
        return schedules;
    }

    private double parseTimeValue(String time) {
        time = time.replaceAll("[^0-9AB]", "");
        if (time.isEmpty()) return 0.0;

        if (time.contains("A")) {
            return Double.parseDouble(time.replace("A", ""));
        } else if (time.contains("B")) {
            return Double.parseDouble(time.replace("B", "")) + 0.5;
        } else {
            return Double.parseDouble(time);
        }
    }

    // Helper methods
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                } else {
                    yield String.valueOf((int) cell.getNumericCellValue());
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    private String getStringValue(JsonNode node, String fieldName, String defaultValue) {
        JsonNode field = node.path(fieldName);
        return (field.isMissingNode() || field.isNull()) ? defaultValue : field.asText();
    }

    private String getStringValue(JsonNode node, String fieldName) {
        return getStringValue(node, fieldName, null);
    }

    private Integer getIntValue(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        return (field.isMissingNode() || field.isNull()) ? null : field.asInt();
    }

    private Boolean getBooleanValue(JsonNode node, String fieldName, boolean defaultValue) {
        JsonNode field = node.path(fieldName);
        return (field.isMissingNode() || field.isNull()) ? defaultValue : field.asBoolean();
    }

    private SubjectType parseSubjectType(String type) {
        if (type == null || type.isBlank()) return SubjectType.일선;
        return switch (type.trim()) {
            case "전심", "전공심화" -> SubjectType.전심;
            case "전핵", "전공핵심" -> SubjectType.전핵;
            case "심교", "심화교양" -> SubjectType.심교;
            case "핵교", "핵심교양" -> SubjectType.핵교;
            case "기교", "기초교양" -> SubjectType.기교;
            case "전기", "전공기초" -> SubjectType.전기;
            case "군사학" -> SubjectType.군사학;
            case "교직" -> SubjectType.교직;
            default -> SubjectType.일선;
        };
    }

    private ClassMethod parseClassMethod(String method) {
        if (method == null || method.isBlank()) return ClassMethod.OFFLINE;
        String normalized = method.trim().toUpperCase();
        if (normalized.contains("ONLINE") || normalized.contains("온라인") || normalized.contains("비대면")) {
            return ClassMethod.ONLINE;
        }
        if (normalized.contains("BLENDED") || normalized.contains("블렌디드")) {
            return ClassMethod.BLENDED;
        }
        return ClassMethod.OFFLINE;
    }
}
