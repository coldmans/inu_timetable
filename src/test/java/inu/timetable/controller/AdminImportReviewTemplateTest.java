package inu.timetable.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminImportReviewTemplateTest {

    @Test
    void acceptsJsonAndExcelFilesInReviewUpload() throws Exception {
        String html = new ClassPathResource("templates/admin/import-review.html")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html)
                .contains("JSON·Excel 업로드·검증")
                .contains(".json,.xlsx")
                .contains("const supportedExtensions = [\".json\", \".xlsx\"]")
                .contains("강의계획서 JSON 또는 종합강의시간표 Excel");
    }
}
