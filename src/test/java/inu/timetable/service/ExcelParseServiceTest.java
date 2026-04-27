package inu.timetable.service;

import inu.timetable.repository.SubjectRepository;
import inu.timetable.repository.WishlistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExcelParseServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private WishlistRepository wishlistRepository;

    @Spy
    @InjectMocks
    private ExcelParseService excelParseService;

    @Test
    void parseAndSaveSubjectsReplace_isDisabledToProtectExistingData() throws Exception {
        // Given
        MultipartFile mockFile = mock(MultipartFile.class);

        // When
        assertThrows(UnsupportedOperationException.class,
                () -> excelParseService.parseAndSaveSubjectsReplace(mockFile));

        // Then
        verify(wishlistRepository, never()).deleteAll();
        verify(subjectRepository, never()).deleteAll();
        verify(subjectRepository, never()).saveAll(anyList());
    }
}
