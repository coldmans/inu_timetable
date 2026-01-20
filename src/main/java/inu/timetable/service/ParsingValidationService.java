package inu.timetable.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Service
public class ParsingValidationService {

    public Map<String, Object> validatePdfParsing(MultipartFile file, String semester) {
        Map<String, Object> report = new HashMap<>();
        report.put("parsedCount", 0);
        report.put("dbCount", 0);
        report.put("message", "Not implemented");
        return report;
    }

    public Map<String, Object> validateExcelParsing(MultipartFile file, String semester) {
        Map<String, Object> report = new HashMap<>();
        report.put("parsedCount", 0);
        report.put("dbCount", 0);
        report.put("message", "Not implemented");
        return report;
    }
}
