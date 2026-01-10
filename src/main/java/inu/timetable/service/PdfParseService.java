package inu.timetable.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.entity.Schedule;
import inu.timetable.entity.Subject;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.repository.SubjectRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class PdfParseService {

  private final SubjectRepository subjectRepository;
  private final DepartmentMappingService departmentMappingService;
  private final WebClient webClient = WebClient.builder().build();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${gemini.api.key}")
  private String geminiApiKey;

  @Autowired
  public PdfParseService(SubjectRepository subjectRepository, DepartmentMappingService departmentMappingService) {
    this.subjectRepository = subjectRepository;
    this.departmentMappingService = departmentMappingService;
  }

  public int parseAndSaveSubjects(MultipartFile file) throws IOException {
    System.out.println("=== PDF 파싱 시작 ===");

    Resource resource = new InputStreamResource(file.getInputStream());
    PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
    List<Document> documents = pdfReader.get();

    System.out.println("PDF 페이지 수: " + documents.size());

    List<Subject> allSubjects = new ArrayList<>();

    for (int i = 0; i < documents.size(); i++) {
      Document page = documents.get(i);
      System.out.println("\n=== 페이지 " + (i + 1) + " 처리 시작 ===");

      String pageContent = extractTextFromDocument(page);

      if (pageContent == null) {
        System.out.println("페이지 " + (i + 1) + " 스킵: 텍스트 콘텐츠 없음 (null)");
        continue;
      }
      if (pageContent.trim().length() < 100) {
        System.out.println("페이지 " + (i + 1) + " 스킵: 텍스트 콘텐츠 없음 (길이: " + pageContent.trim().length() + ")");
        continue;
      }
      if (!containsSubjectData(pageContent)) {
        System.out.println("페이지 " + (i + 1) + " 스킵: 과목 데이터 없음");
        System.out.println("페이지 텍스트 샘플: " + pageContent.substring(0, Math.min(300, pageContent.length())));
        continue;
      }
      System.out.println("페이지 내용 길이: " + pageContent.length());

      List<Subject> pageSubjects = parsePageWithGemini(pageContent, i + 1);
      System.out.println("페이지 " + (i + 1) + "에서 추출된 과목 수: " + pageSubjects.size());

      allSubjects.addAll(pageSubjects);
    }

    System.out.println("\n=== 데이터베이스 저장 시작 ===");
    System.out.println("전체 추출된 과목 수: " + allSubjects.size());

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

  private String extractTextFromDocument(Document document) {
    // Spring AI Document에서 직접 콘텐츠 가져오기
    String content = document.getText();
    System.out.println("문서 텍스트 길이: " + content.length());
    System.out.println("문서 텍스트 미리보기: " + content.substring(0, Math.min(200, content.length())));
    return content;
  }

  private boolean containsSubjectData(String content) {
    return content.contains("전기")
        || content.contains("전핵")
        || content.contains("전심")
        || content.contains("기교")
        || content.contains("학점")
        || content.contains("교수")
        || content.contains("요일")
        || content.contains("교시");
  }

  private List<Subject> parseBatchWithGemini(List<String> batchContents, List<Integer> pageNumbers) {
    try {
      StringBuilder combinedContent = new StringBuilder();
      for (int i = 0; i < batchContents.size(); i++) {
        combinedContent.append("=== 페이지 ").append(pageNumbers.get(i)).append(" ===\n");
        combinedContent.append(batchContents.get(i)).append("\n\n");
      }

      String prompt =
          """
                다음은 대학교 시간표 PDF 데이터입니다. 여러 페이지의 데이터를 정확하게 JSON 형식으로 파싱해주세요.

                **중요한 파싱 규칙:**
                1. 학과/학부 정보:
                   - 일반 과목: "국어국문학과", "영어영문학과", "컴퓨터공학부" 등의 형태로 나타나며, 해당 페이지의 모든 과목이 그 학과에 속함
                   - 기초교양 과목: "독문,불문,중국" 같은 줄임말로 나타날 수 있음 → **원본 그대로** department에 저장
                2. 빈 값 처리: 교수명이나 요일이 비어있으면 null로 설정
                3. 요일 처리: 요일이 없는 과목도 있을 수 있음 (예: 집중수업, 온라인 과목 등)
                4. 야간수업: "야" 키워드가 있으면 isNight를 true로 설정
                5. 이수구분: 전심(전공심화), 전핵(전공핵심), 기교(기초교양), 전기(전공기초), 일선(일반선택), 심교(심화교양), 핵교(핵심교양)

                각 과목에 대해 다음 정보를 추출해주세요:
                - subjectName: 교과목명 (필수, 문자열)
                - credits: 학점 (필수, 숫자)
                - professor: 교수명 (문자열, 비어있으면 null)
                - timeString: 요일 및 교시 원본 문자열 (예: "월 4-5A 5B-6", "화 1-2", "금 6A-7")
                - isNight: 야간 수업 여부 (true/false, "야"가 포함되면 true)
                - subjectType: 이수구분 (전심, 전핵, 심교, 핵교, 일선, 기교, 전기 중 하나)
                - classMethod: 수업방법 (ONLINE, OFFLINE, BLENDED 중 하나, 기본값 OFFLINE)
                - grade: 학년 (1-4 중 하나, 없으면 null)
                - department: 학과명 (정식 학과명 또는 "독문,불문,중국" 같은 줄임말을 **원본 그대로** 저장)

                응답은 다음과 같은 JSON 배열 형태로만 주세요 (JSON 외에 다른 설명 금지):
                [
                  {
                    "subjectName": "운영체제",
                    "credits": 3,
                    "professor": "김교수",
                    "timeString": "월 6-7A",
                    "isNight": false,
                    "subjectType": "전심",
                    "classMethod": "OFFLINE",
                    "grade": 3,
                    "department": "컴퓨터공학부"
                  },
                  {
                    "subjectName": "Academic English 2",
                    "credits": 2,
                    "professor": "김상조",
                    "timeString": "목 7-8",
                    "isNight": false,
                    "subjectType": "기교",
                    "classMethod": "OFFLINE",
                    "grade": null,
                    "department": "독문,불문,중국"
                  }
                ]

                PDF 페이지 내용들:
                """
              + combinedContent.toString();

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

      // 배치 응답을 파일로 저장
      saveBatchGeminiResponse(pageNumbers, combinedContent.toString(), response);

      return parseGeminiResponse(response);

    } catch (Exception e) {
      System.err.println("배치 " + pageNumbers + " Gemini API 호출 실패: " + e.getMessage());
      e.printStackTrace();
      return new ArrayList<>();
    }
  }

  private List<Subject> parsePageWithGemini(String pageContent, int pageNumber) {
    try {
      String prompt =
          """
                다음은 대학교 시간표 PDF 데이터입니다. 이를 정확하게 JSON 형식으로 파싱해주세요.

                **중요한 파싱 규칙:**
                1. 학과/학부 정보:
                   - 일반 과목: "국어국문학과", "영어영문학과", "컴퓨터공학부" 등의 형태로 나타나며, 해당 페이지의 모든 과목이 그 학과에 속함
                   - 기초교양 과목: "독문,불문,중국" 같은 줄임말로 나타날 수 있음 → **원본 그대로** department에 저장
                2. 빈 값 처리: 교수명이나 요일이 비어있으면 null로 설정
                3. 요일 처리: 요일이 없는 과목도 있을 수 있음 (예: 집중수업, 온라인 과목 등)
                4. 야간수업: "야" 키워드가 있으면 isNight를 true로 설정
                5. 이수구분: 전심(전공심화), 전핵(전공핵심), 기교(기초교양), 전기(전공기초), 일선(일반선택), 심교(심화교양), 핵교(핵심교양)

                각 과목에 대해 다음 정보를 추출해주세요:
                - subjectName: 교과목명 (필수, 문자열)
                - credits: 학점 (필수, 숫자)
                - professor: 교수명 (문자열, 비어있으면 null)
                - timeString: 요일 및 교시 원본 문자열 (예: "월 4-5A 5B-6", "화 1-2", "금 6A-7")
                - isNight: 야간 수업 여부 (true/false, "야"가 포함되면 true)
                - subjectType: 이수구분 (전심, 전핵, 심교, 핵교, 일선, 기교, 전기 중 하나)
                - classMethod: 수업방법 (ONLINE, OFFLINE, BLENDED 중 하나, 기본값 OFFLINE)
                - grade: 학년 (1-4 중 하나, 없으면 null)
                - department: 학과명 (정식 학과명 또는 "독문,불문,중국" 같은 줄임말을 **원본 그대로** 저장)

                응답은 다음과 같은 JSON 배열 형태로만 주세요 (JSON 외에 다른 설명 금지):
                [
                  {
                    "subjectName": "운영체제",
                    "credits": 3,
                    "professor": "김교수",
                    "timeString": "월 6-7A",
                    "isNight": false,
                    "subjectType": "전심",
                    "classMethod": "OFFLINE",
                    "grade": 3,
                    "department": "컴퓨터공학부"
                  },
                  {
                    "subjectName": "Academic English 2",
                    "credits": 2,
                    "professor": "김상조",
                    "timeString": "목 7-8",
                    "isNight": false,
                    "subjectType": "기교",
                    "classMethod": "OFFLINE",
                    "grade": null,
                    "department": "독문,불문,중국"
                  }
                ]

                PDF 페이지 내용:
                """
              + pageContent;

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

      // Gemini 응답을 파일로 저장
      saveGeminiResponse(pageNumber, pageContent, response);

      return parseGeminiResponse(response);

    } catch (Exception e) {
      System.err.println("=== 페이지 " + pageNumber + " Gemini API 호출 실패 ===");
      System.err.println("에러 타입: " + e.getClass().getSimpleName());
      System.err.println("에러 메시지: " + e.getMessage());
      if (e.getCause() != null) {
        System.err.println("원인: " + e.getCause().getMessage());
      }
      e.printStackTrace();
      
      // 재시도 로직 추가
      System.out.println("페이지 " + pageNumber + " 처리 실패 - 빈 결과 반환");
      return new ArrayList<>();
    }
  }

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

      // JSON 파싱 - 강제로 배열로 처리
      System.out.println("원본 JSON 길이: " + content.length());
      System.out.println("JSON 시작: " + content.substring(0, Math.min(50, content.length())));

      // content가 배열인지 확인
      String trimmedContent = content.trim();
      if (trimmedContent.startsWith("[") && trimmedContent.endsWith("]")) {
        System.out.println("JSON이 배열 형태임을 확인");

        // TypeReference를 사용해서 직접 List로 파싱
        try {
          com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>> typeRef
            = new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {};
          java.util.List<java.util.Map<String, Object>> subjectMaps = objectMapper.readValue(content, typeRef);

          System.out.println("직접 파싱 성공 - 과목 수: " + subjectMaps.size());

          for (java.util.Map<String, Object> subjectMap : subjectMaps) {
            JsonNode subjectNode = objectMapper.valueToTree(subjectMap);
            List<Subject> parsedSubjects = parseSubjectsFromJson(subjectNode);
            subjects.addAll(parsedSubjects);
          }
        } catch (Exception directParseException) {
          System.err.println("직접 파싱 실패: " + directParseException.getMessage());
          directParseException.printStackTrace();
        }
      } else {
        System.err.println("JSON이 배열 형태가 아님: " + trimmedContent.substring(0, Math.min(100, trimmedContent.length())));

        // 객체 안에 배열이 있는 경우 처리 ({"courses": [...]} 또는 {"subjects": [...]})
        try {
          JsonNode rootNode = objectMapper.readTree(content);
          JsonNode arrayNode = null;

          // courses, subjects, data 등의 키에서 배열 찾기
          String[] possibleKeys = {"courses", "subjects", "data", "items", "list"};
          for (String key : possibleKeys) {
            if (rootNode.has(key) && rootNode.get(key).isArray()) {
              arrayNode = rootNode.get(key);
              System.out.println("'" + key + "' 키에서 배열 발견 - 크기: " + arrayNode.size());
              break;
            }
          }

          if (arrayNode != null) {
            for (JsonNode subjectNode : arrayNode) {
              List<Subject> parsedSubjects = parseSubjectsFromJson(subjectNode);
              subjects.addAll(parsedSubjects);
            }
          } else {
            System.err.println("적절한 배열을 찾을 수 없음");
          }
        } catch (Exception objectParseException) {
          System.err.println("객체 파싱 실패: " + objectParseException.getMessage());
        }
      }
    } catch (Exception e) {
      System.err.println("=== Gemini 응답 파싱 실패 ===");
      System.err.println("에러 타입: " + e.getClass().getSimpleName());
      System.err.println("에러 메시지: " + e.getMessage());
      e.printStackTrace();
      System.err.println("원본 응답 길이: " + response.length());
      System.err.println("원본 응답 앞부분: " + response.substring(0, Math.min(500, response.length())));
    }
    return subjects;
  }

  /**
   * JSON 노드에서 Subject 리스트 생성 (학과 줄임말 처리 포함)
   * 학과 필드에 쉼표가 있으면 여러 학과로 분리하여 각각 Subject 생성
   */
  private List<Subject> parseSubjectsFromJson(JsonNode node) {
    List<Subject> subjects = new ArrayList<>();
    try {
      String subjectName = getStringValue(node, "subjectName");
      Integer credits = getIntValue(node, "credits");

      System.out.println("디버깅 - subjectName: " + subjectName + ", credits: " + credits);

      if (subjectName == null || credits == null) {
        System.err.println("필수 필드(subjectName, credits)가 누락되어 파싱을 건너뜁니다: " + node.toString());
        return subjects;
      }

      String departmentField = getStringValue(node, "department", "미분류");
      List<String> departments = new ArrayList<>();

      // 학과 필드에 쉼표가 있으면 줄임말로 간주하고 변환
      if (departmentField.contains(",") || departmentField.contains("，")) {
        System.out.println("학과 줄임말 발견: " + departmentField);
        List<String> parsedDepartments = departmentMappingService.parseAbbreviations(departmentField);
        departments.addAll(parsedDepartments);
        System.out.println("변환된 학과 목록: " + parsedDepartments);
      } else {
        departments.add(departmentField);
      }

      // 각 학과별로 Subject 생성
      for (String department : departments) {
        String timeString = getStringValue(node, "timeString");
        List<Schedule> schedules = parseTime(timeString);

        Subject subject =
            Subject.builder()
                .subjectName(subjectName)
                .credits(credits)
                .professor(getStringValue(node, "professor", "미배정"))
                .isNight(getBooleanValue(node, "isNight", false))
                .subjectType(parseSubjectType(getStringValue(node, "subjectType")))
                .classMethod(parseClassMethod(getStringValue(node, "classMethod")))
                .grade(getIntValue(node, "grade"))
                .department(department)
                .schedules(new ArrayList<>())
                .build();

        // Schedule 복사 (각 Subject마다 별도의 Schedule 인스턴스 필요)
        for (Schedule schedule : schedules) {
          Schedule newSchedule = Schedule.builder()
              .dayOfWeek(schedule.getDayOfWeek())
              .startTime(schedule.getStartTime())
              .endTime(schedule.getEndTime())
              .subject(subject)
              .build();
          subject.getSchedules().add(newSchedule);
        }

        subjects.add(subject);
      }

      return subjects;
    } catch (Exception e) {
      System.err.println("Subject JSON 파싱 오류: " + e.getMessage() + " | Node: " + node.toString());
      e.printStackTrace();
      return subjects;
    }
  }

  /**
   * 단일 Subject 생성 (하위 호환성을 위해 유지)
   */
  private Subject parseSubjectFromJson(JsonNode node) {
    List<Subject> subjects = parseSubjectsFromJson(node);
    return subjects.isEmpty() ? null : subjects.get(0);
  }

  private List<Schedule> parseTime(String timeString) {
    List<Schedule> schedules = new ArrayList<>();
    if (timeString == null || timeString.isBlank()) {
      return schedules;
    }

    // 괄호 안의 강의실 정보 제거
    String cleanTimeString = timeString.replaceAll("\\([^)]*\\)", "").trim();
    
    // 요일별로 시간 파싱
    Pattern dayPattern = Pattern.compile("([월화수목금토일])\\s+([^월화수목금토일]+)");
    Matcher dayMatcher = dayPattern.matcher(cleanTimeString);

    while (dayMatcher.find()) {
      String dayOfWeek = dayMatcher.group(1);
      String timeSlots = dayMatcher.group(2).trim();

      double minStartTime = Double.MAX_VALUE;
      double maxEndTime = Double.MIN_VALUE;

      // 하이픈으로 구분된 시간 범위 처리
      Pattern rangePattern = Pattern.compile("((?:야)?[1-9][0-9]?[AB]?)-((?:야)?[1-9][0-9]?[AB]?)");
      Matcher rangeMatcher = rangePattern.matcher(timeSlots);
      
      boolean hasRange = false;
      while (rangeMatcher.find()) {
        String startPeriod = rangeMatcher.group(1);
        String endPeriod = rangeMatcher.group(2);
        
        double start = convertToTime(startPeriod);
        double end = convertToTimeEnd(endPeriod, startPeriod); // 시작 시간 정보도 전달
        minStartTime = Math.min(minStartTime, start);
        maxEndTime = Math.max(maxEndTime, end);
        hasRange = true;
      }

      // 하이픈이 없는 경우: 공백으로 구분된 연속 시간 처리
      if (!hasRange) {
        List<Double> times = new ArrayList<>();
        // 야간교시와 일반교시 모두 매칭
        Pattern timePattern = Pattern.compile("(야[1-3]|[1-9][0-9]?[AB]?)");
        Matcher timeMatcher = timePattern.matcher(timeSlots);
        
        while (timeMatcher.find()) {
          times.add(convertToTime(timeMatcher.group(1)));
        }
        
        if (!times.isEmpty()) {
          minStartTime = times.get(0);
          maxEndTime = times.get(times.size() - 1) + 1.0; // 마지막 교시 끝시간
        }
      }

      if (minStartTime != Double.MAX_VALUE) {
        // 야간 과목에서 startTime > endTime인 경우 endTime에 8을 더함
        if (minStartTime > maxEndTime) {
          maxEndTime += 8.0;
        }
        schedules.add(
            Schedule.builder().dayOfWeek(dayOfWeek).startTime(minStartTime).endTime(maxEndTime).build());
      }
    }
    return schedules;
  }

  private double convertToTime(String period) {
    // 야간교시 처리
    if (period.startsWith("야")) {
      String numericPart = period.substring(1);
      if (numericPart.equals("1")) return 10.0;
      if (numericPart.equals("2")) return 11.0;
      if (numericPart.equals("3")) return 12.0;
      return 10.0; // 기본값
    }
    
    String numericPart = period.replaceAll("[^0-9]", "");
    if (numericPart.isEmpty()) return 0.0;
    double time = Double.parseDouble(numericPart);
    
    if (period.contains("A")) {
      // A는 전반부: 해당 교시 시작 시간
      return time;
    } else if (period.contains("B")) {
      // B는 후반부: 해당 교시 + 0.5
      return time + 0.5;
    } else {
      // 일반 교시: 해당 교시 시작 시간
      return time;
    }
  }

  private double convertToTimeEnd(String period, String startPeriod) {
    // 야간교시 처리
    if (period.startsWith("야")) {
      String numericPart = period.substring(1);
      if (numericPart.equals("1")) return 11.0;
      if (numericPart.equals("2")) return 12.0;
      if (numericPart.equals("3")) return 13.0;
      return 11.0; // 기본값
    }
    
    // 시작이 야간이면 끝 시간도 야간으로 해석
    if (startPeriod.startsWith("야")) {
      String numericPart = period.replaceAll("[^0-9]", "");
      if (numericPart.isEmpty()) return 11.0;
      int nightTime = Integer.parseInt(numericPart);
      
      if (period.contains("A")) {
        // 야간 A: 야간교시 + 0.5까지
        return (9 + nightTime) + 0.5;
      } else if (period.contains("B")) {
        // 야간 B: 다음 야간교시까지
        return (9 + nightTime) + 1.0;
      } else {
        // 야간 일반: 다음 야간교시까지
        return (9 + nightTime) + 1.0;
      }
    }
    
    String numericPart = period.replaceAll("[^0-9]", "");
    if (numericPart.isEmpty()) return 0.0;
    double time = Double.parseDouble(numericPart);
    
    if (period.contains("A")) {
      // A는 전반부: 해당 교시 + 0.5까지
      return time + 0.5;
    } else if (period.contains("B")) {
      // B는 후반부: 다음 교시까지
      return time + 1.0;
    } else {
      // 일반 교시: 다음 교시까지
      return time + 1.0;
    }
  }

  // 기존 호환성을 위한 오버로드
  private double convertToTimeEnd(String period) {
    return convertToTimeEnd(period, "");
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

  private ClassMethod parseClassMethod(String method) {
    if (method == null) return ClassMethod.OFFLINE;
    return switch (method.toUpperCase()) {
      case "ONLINE" -> ClassMethod.ONLINE;
      case "BLENDED" -> ClassMethod.BLENDED;
      default -> ClassMethod.OFFLINE;
    };
  }

  private SubjectType parseSubjectType(String type) {
    if (type == null) return SubjectType.일선;
    return switch (type) {
      case "전심" -> SubjectType.전심;
      case "전핵" -> SubjectType.전핵;
      case "심교" -> SubjectType.심교;
      case "핵교" -> SubjectType.핵교;
      case "기교" -> SubjectType.기교;
      case "전기" -> SubjectType.전기;
      case "군사학" -> SubjectType.군사학;
      case "교직" -> SubjectType.교직;
      default -> SubjectType.일선;
    };
  }

  private void saveBatchGeminiResponse(List<Integer> pageNumbers, String batchContent, String geminiResponse) {
    try {
      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
      String pageRange = pageNumbers.get(0) + "-" + pageNumbers.get(pageNumbers.size() - 1);
      String fileName = "gemini_batch_response_pages_" + pageRange + "_" + timestamp + ".txt";
      Path filePath = Paths.get(fileName);
      
      StringBuilder content = new StringBuilder();
      content.append("=== BATCH ANALYSIS ===\n");
      content.append("Pages: ").append(pageNumbers).append("\n");
      content.append("Timestamp: ").append(timestamp).append("\n\n");
      content.append("=== ORIGINAL PDF CONTENT ===\n");
      content.append(batchContent).append("\n\n");
      content.append("=== GEMINI RESPONSE ===\n");
      content.append(geminiResponse).append("\n");
      
      Files.write(filePath, content.toString().getBytes());
      System.out.println("배치 Gemini 응답 저장됨: " + fileName);
    } catch (IOException e) {
      System.err.println("배치 Gemini 응답 저장 실패: " + e.getMessage());
    }
  }

  private void saveGeminiResponse(int pageNumber, String pageContent, String geminiResponse) {
    try {
      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
      String fileName = "gemini_response_page_" + pageNumber + "_" + timestamp + ".txt";
      Path filePath = Paths.get(fileName);
      
      StringBuilder content = new StringBuilder();
      content.append("=== PAGE ").append(pageNumber).append(" ANALYSIS ===\n");
      content.append("Timestamp: ").append(timestamp).append("\n\n");
      content.append("=== ORIGINAL PDF CONTENT ===\n");
      content.append(pageContent).append("\n\n");
      content.append("=== GEMINI RESPONSE ===\n");
      content.append(geminiResponse).append("\n");
      
      Files.write(filePath, content.toString().getBytes());
      System.out.println("Gemini 응답 저장됨: " + fileName);
    } catch (IOException e) {
      System.err.println("Gemini 응답 저장 실패: " + e.getMessage());
    }
  }
}
