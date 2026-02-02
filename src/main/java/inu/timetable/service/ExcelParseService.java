package inu.timetable.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.UserRepository;
import inu.timetable.repository.UserTimetableRepository;
import inu.timetable.repository.WishlistRepository;
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
    private final WishlistRepository wishlistRepository;
    private final UserTimetableRepository userTimetableRepository;
    private final UserRepository userRepository;
    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    /**
     * [DISABLED] Excel 파싱 후 DB 전체 교체 (기존 데이터 삭제)
     * 데이터 보호를 위해 비활성화됨. parseAndSaveSubjectsIncremental을 사용하세요.
     */
    public int parseAndSaveSubjectsReplace(MultipartFile file) throws IOException {
        // [DISABLED] 전체 삭제 기능 비활성화 - 사용자 데이터 보호를 위해 주석 처리됨
        throw new UnsupportedOperationException("전체 삭제 기능이 비활성화되었습니다. parseAndSaveSubjectsIncremental을 사용하세요.");

        // // 1. 파싱 먼저 수행 (시간이 오래 걸리는 작업, DB 트랜잭션 없이 진행)
        // System.out.println("=== Excel 파싱 시작 (메모리 로드) ===");
        // List<Subject> allSubjects = parseWithoutSaving(file, 0);
        // System.out.println("=== Excel 파싱 완료 (" + allSubjects.size() + "과목) ===");

        // if (allSubjects.isEmpty()) {
        //     System.out.println("저장할 과목이 없습니다.");
        //     return 0;
        // }

        // // 2. DB 교체 (여기서부터 트랜잭션 시작)
        // System.out.println("\n=== 데이터베이스 교체 트랜잭션 시작 ===");
        // transactionTemplate.execute(status -> {
        //     try {
        //         // 기존 데이터 삭제
        //         System.out.println("기존 시간표 데이터 삭제 중...");
        //         userTimetableRepository.deleteAll();
        //         System.out.println("기존 장바구니 데이터 삭제 중...");
        //         wishlistRepository.deleteAll();
        //         System.out.println("기존 사용자 데이터 삭제 중...");
        //         userRepository.deleteAll();
        //         System.out.println("기존 과목 데이터 삭제 중...");
        //         subjectRepository.deleteAll();
        //         System.out.println("=== 기존 데이터 삭제 완료 ===");

        //         // 신규 데이터 저장
        //         List<Subject> savedSubjects = subjectRepository.saveAll(allSubjects);
        //         System.out.println("성공적으로 저장된 과목 수: " + savedSubjects.size());
        //         return savedSubjects.size();
        //     } catch (Exception e) {
        //         System.err.println("데이터베이스 교체 중 오류 발생: " + e.getMessage());
        //         e.printStackTrace();
        //         status.setRollbackOnly(); // 롤백
        //         throw new RuntimeException("DB 교체 실패", e);
        //     }
        // });
        // System.out.println("=== 데이터베이스 교체 트랜잭션 종료 ===\n");

        // return 0;
    }

    /**
     * Excel 파싱 후 중복 체크하여 새로운 과목만 추가 (기존 데이터 유지)
     */
    @org.springframework.transaction.annotation.Transactional
    public int parseAndSaveSubjectsIncremental(MultipartFile file) throws IOException {
        List<Subject> allSubjects = parseWithoutSaving(file, 0);

        System.out.println("\n=== 데이터베이스 저장 시작 (증분 모드) ===");
        System.out.println("전체 추출된 과목 수: " + allSubjects.size());

        if (!allSubjects.isEmpty()) {
            try {
                // 중복 방지 로직: DB에 있는 모든 과목을 가져와서 비교 (스케줄 포함)
                List<Subject> existingSubjects = subjectRepository.findAllWithSchedules();
                System.out.println("기존 DB 과목 수: " + existingSubjects.size());

                List<Subject> newSubjects = new ArrayList<>();
                int duplicateCount = 0;

                for (Subject parsedSubject : allSubjects) {
                    boolean isDuplicate = existingSubjects.stream()
                            .anyMatch(existing -> isSameSubject(existing, parsedSubject));

                    if (!isDuplicate) {
                        newSubjects.add(parsedSubject);
                    } else {
                        duplicateCount++;
                    }
                }

                System.out.println("중복 제외된 과목 수: " + duplicateCount);
                System.out.println("새로 저장할 과목 수: " + newSubjects.size());

                if (!newSubjects.isEmpty()) {
                    List<Subject> savedSubjects = subjectRepository.saveAll(newSubjects);
                    System.out.println("성공적으로 저장된 과목 수: " + savedSubjects.size());
                } else {
                    System.out.println("저장할 새로운 과목이 없습니다.");
                }
            } catch (Exception e) {
                System.err.println("데이터베이스 저장 오류: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
        } else {
            System.out.println("파싱된 과목이 없습니다.");
        }

        return allSubjects.size();
    }

    /**
     * Excel 파싱만 수행 (DB 저장 안 함)
     * 검증/테스트 용도로 사용
     */
    public List<Subject> parseWithoutSaving(MultipartFile file, int maxChunks) throws IOException {
        System.out.println("=== Excel 파싱 시작 (AI 기반, 저장 안 함) ===");
        if (maxChunks > 0) {
            System.out.println("테스트 모드: 최대 " + maxChunks + "개 청크만 처리합니다.");
        }

        List<Subject> allSubjects = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
                Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            int totalRows = sheet.getLastRowNum();
            System.out.println("Sheet 이름: " + sheet.getSheetName());
            System.out.println("전체 행 수: " + totalRows);

            // 청크 크기 (한 번에 처리할 행 수) - 작게 해서 응답 잘림 방지
            int chunkSize = 50;
            int totalChunks = (int) Math.ceil((double) totalRows / chunkSize);

            // 청크별로 처리
            int limit = (maxChunks > 0) ? Math.min(totalChunks, maxChunks) : totalChunks;
            for (int chunkIndex = 0; chunkIndex < limit; chunkIndex++) {
                int startRow = chunkIndex * chunkSize + 1; // 헤더 제외
                int endRow = Math.min(startRow + chunkSize - 1, totalRows);

                System.out.println("\n=== 청크 " + (chunkIndex + 1) + "/" + totalChunks +
                        " 처리 중 (행 " + startRow + "-" + endRow + ") ===");

                // 청크 텍스트 추출
                String chunkText = extractTextFromExcelChunk(sheet, startRow, endRow);
                System.out.println("청크 텍스트 길이: " + chunkText.length() + " 문자");

                // Gemini AI로 파싱
                List<Subject> chunkSubjects = parseWithGemini(chunkText);
                System.out.println("청크 파싱 완료: " + chunkSubjects.size() + "개 과목 추출");

                allSubjects.addAll(chunkSubjects);

                // 무료 티어 제한 고려 (분당 15 RPM), 마지막 청크가 아니면 대기
                if (chunkIndex < totalChunks - 1) {
                    try {
                        System.out.println("다음 청크 처리를 위해 4초 대기...");
                        Thread.sleep(4000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        System.out.println("\n=== 전체 파싱 완료: " + allSubjects.size() + "개 과목 추출 ===");
        return allSubjects;
    }

    /**
     * Excel 파일의 특정 행 범위를 텍스트로 변환 (청크)
     */
    private String extractTextFromExcelChunk(Sheet sheet, int startRow, int endRow) {
        StringBuilder text = new StringBuilder();

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

        // 데이터 행 추가 (지정된 범위만)
        text.append("=== 데이터 ===\n");
        for (int rowIndex = startRow; rowIndex <= endRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null)
                continue;

            // 각 셀의 값을 탭으로 구분하여 추가
            for (int colIndex = 0; colIndex < 17; colIndex++) { // 17개 컬럼
                Cell cell = row.getCell(colIndex);
                String value = getCellValueAsString(cell);
                text.append(value != null ? value : "").append("\t");
            }
            text.append("\n");
        }

        return text.toString();
    }

    /**
     * Gemini AI로 Excel 텍스트 파싱
     */
    private List<Subject> parseWithGemini(String excelText) {
        try {
            String prompt = """
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
                       예: [15-201:월(5B-6)] → "월 5B-6" (중요: A, B 접미사 유지)
                       예: [15-201:월(야1-야2)] → "월 야1-야2" (중요: 야간 표시 유지)

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

            String requestBody = """
                    {
                      "contents": [{"parts": [{"text": "%s"}]}],
                      "generationConfig": {
                        "temperature": 0.1, "topK": 16, "topP": 0.95, "maxOutputTokens": 8192
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

            String response = webClient
                    .post()
                    .uri(
                            "https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash:generateContent?key="
                                    + geminiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(
                            status -> status.isError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(errorBody -> {
                                        System.err.println("=== Gemini API 에러 응답 ===");
                                        System.err.println(errorBody);
                                        return new RuntimeException("API Error: " + errorBody);
                                    }))
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

            System.out.println("JSON 길이: " + content.length() + " 문자");
            if (content.length() < 500) {
                System.out.println("=== Gemini 응답 (전체) ===");
                System.out.println(content);
            } else {
                System.out.println("=== Gemini 응답 (처음 200자) ===");
                System.out.println(content.substring(0, 200) + "...");
            }

            // 마크다운 코드 블록 제거 (```json ... ``` 또는 ``` ... ```)
            String trimmedContent = content.trim();
            if (trimmedContent.startsWith("```")) {
                int startIndex = trimmedContent.indexOf('\n');
                int endIndex = trimmedContent.lastIndexOf("```");

                // 시작 부분 제거
                if (startIndex > 0) {
                    if (endIndex > startIndex) {
                        // 정상: 시작과 끝 모두 제거
                        trimmedContent = trimmedContent.substring(startIndex + 1, endIndex).trim();
                    } else {
                        // 응답이 잘린 경우: 시작 부분만 제거
                        trimmedContent = trimmedContent.substring(startIndex + 1).trim();
                        System.out.println("경고: 응답이 잘렸습니다 (마지막 ``` 없음)");
                    }
                    System.out.println("마크다운 제거 후 길이: " + trimmedContent.length());
                }
            }

            if (trimmedContent.startsWith("[")) {
                // JSON이 배열로 시작하면 파싱 시도
                // 잘린 경우 수정해서 파싱
                if (!trimmedContent.endsWith("]")) {
                    System.out.println("경고: JSON이 ]로 끝나지 않음, 마지막 불완전한 항목을 제거하고 ] 추가");
                    // 마지막 { 이후를 제거하고 ] 추가
                    int lastOpenBrace = trimmedContent.lastIndexOf("{");
                    if (lastOpenBrace > 0) {
                        trimmedContent = trimmedContent.substring(0, lastOpenBrace).trim();
                        // 마지막 쉼표 제거
                        if (trimmedContent.endsWith(",")) {
                            trimmedContent = trimmedContent.substring(0, trimmedContent.length() - 1);
                        }
                        trimmedContent += "\n]";
                        System.out.println("수정된 JSON 길이: " + trimmedContent.length());
                    }
                }

                com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>> typeRef = new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {
                };
                java.util.List<java.util.Map<String, Object>> subjectMaps = objectMapper.readValue(trimmedContent,
                        typeRef);

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

            Subject subject = Subject.builder()
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
        List<Schedule> schedules = new ArrayList<>();
        if (timeString == null || timeString.isBlank()) {
            return schedules;
        }

        // 간단한 파싱: "월 7 화 3" 또는 "월 7-9" 또는 "월 1 2"
        String[] parts = timeString.split("\\s+");
        String currentDay = null;

        // 1. Parse into initial list
        for (String part : parts) {
            // 요일 확인
            if (part.length() == 1 && "월화수목금토일".contains(part)) {
                currentDay = part;
                continue;
            }

            // 요일이 설정된 상태에서 시간 파싱
            if (currentDay != null) {
                try {
                    double start, end;

                    // 야간 보정 여부 확인
                    boolean isNightTime = part.contains("야");
                    String timeVal = part.replaceAll("야", ""); // 숫자 파싱을 위해 "야" 제거에는 영향 없으나 로직 일관성 유지

                    if (timeVal.contains("-")) {
                        String[] range = timeVal.split("-");
                        start = parseTimeValue(range[0]);

                        // 종료 시간 계산: A/B는 0.5 더하기, 아니면 1.0 더하기
                        double endVal = parseTimeValue(range[1]);
                        if (range[1].contains("A") || range[1].contains("B")) {
                            end = endVal + 0.5;
                        } else {
                            end = endVal + 1.0;
                        }
                    } else {
                        // 단일 시간
                        start = parseTimeValue(timeVal);
                        if (timeVal.contains("A") || timeVal.contains("B")) {
                            end = start + 0.5;
                        } else {
                            end = start + 1.0;
                        }
                    }

                    if (isNightTime) {
                        start += 9.0;
                        end += 9.0;
                    }

                    schedules.add(
                            Schedule.builder()
                                    .dayOfWeek(currentDay)
                                    .startTime(start)
                                    .endTime(end)
                                    .build());
                } catch (Exception e) {
                    System.err.println("시간 파싱 오류: " + part);
                }
            }
        }

        // 2. Sort by Day and StartTime
        schedules.sort((s1, s2) -> {
            int dayCompare = Integer.compare("월화수목금토일".indexOf(s1.getDayOfWeek()),
                    "월화수목금토일".indexOf(s2.getDayOfWeek()));
            if (dayCompare != 0)
                return dayCompare;
            return Double.compare(s1.getStartTime(), s2.getStartTime());
        });

        // 3. Merge contiguous slots
        if (schedules.size() < 2)
            return schedules;

        List<Schedule> mergedSchedules = new ArrayList<>();
        Schedule current = schedules.get(0);

        for (int i = 1; i < schedules.size(); i++) {
            Schedule next = schedules.get(i);

            // Same day and contiguous time?
            if (current.getDayOfWeek().equals(next.getDayOfWeek()) &&
                    Math.abs(current.getEndTime() - next.getStartTime()) < 0.001) {
                // Merge: extend current's end time
                current.setEndTime(next.getEndTime());
            } else {
                // Not contiguous, add current to list and move to next
                mergedSchedules.add(current);
                current = next;
            }
        }
        // Add the last one
        mergedSchedules.add(current);

        return mergedSchedules;
    }

    private double parseTimeValue(String time) {
        // "야" 제거는 호출 전에 체크하거나 여기서 무시
        time = time.replaceAll("야", "");
        time = time.replaceAll("[^0-9AB]", "");
        if (time.isEmpty())
            return 0.0;

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
        if (cell == null)
            return null;

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
        if (type == null || type.isBlank())
            return SubjectType.일선;
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
        if (method == null || method.isBlank())
            return ClassMethod.OFFLINE;
        String normalized = method.trim().toUpperCase();
        if (normalized.contains("ONLINE") || normalized.contains("온라인") || normalized.contains("비대면")) {
            return ClassMethod.ONLINE;
        }
        if (normalized.contains("BLENDED") || normalized.contains("블렌디드")) {
            return ClassMethod.BLENDED;
        }
        return ClassMethod.OFFLINE;
    }

    /**
     * 두 Subject가 동일한지 비교 (중복 체크용)
     * 과목명, 교수, 학과, 학년, 이수구분, 야간여부, 시간표를 비교
     */
    private boolean isSameSubject(Subject a, Subject b) {
        if (!areEqual(a.getSubjectName(), b.getSubjectName()))
            return false;
        if (!areEqual(a.getProfessor(), b.getProfessor()))
            return false;
        if (!areEqual(a.getDepartment(), b.getDepartment()))
            return false;
        if (!areEqual(a.getGrade(), b.getGrade()))
            return false;
        if (a.getSubjectType() != b.getSubjectType())
            return false;
        if (!areEqual(a.getIsNight(), b.getIsNight()))
            return false;

        // 시간표 비교
        List<Schedule> s1 = a.getSchedules();
        List<Schedule> s2 = b.getSchedules();

        if (s1 == null && s2 == null)
            return true;
        if (s1 == null || s2 == null)
            return false;
        if (s1.size() != s2.size())
            return false;

        // s2의 모든 요소가 s1에 포함되어 있는지 확인 (순서 무관)
        for (Schedule schedule2 : s2) {
            boolean matchFound = s1.stream()
                    .anyMatch(schedule1 -> areEqual(schedule1.getDayOfWeek(), schedule2.getDayOfWeek()) &&
                            Math.abs(schedule1.getStartTime() - schedule2.getStartTime()) < 0.001 &&
                            Math.abs(schedule1.getEndTime() - schedule2.getEndTime()) < 0.001);
            if (!matchFound)
                return false;
        }

        return true;
    }

    /**
     * null-safe 비교
     */
    private boolean areEqual(Object o1, Object o2) {
        if (o1 == null && o2 == null)
            return true;
        if (o1 == null || o2 == null)
            return false;
        return o1.equals(o2);
    }
}
