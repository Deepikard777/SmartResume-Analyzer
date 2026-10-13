package com.resumeanalyzer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeanalyzer.entity.UploadStatus;
import com.resumeanalyzer.repository.ResumeRepository;
import com.resumeanalyzer.service.ResumeTextExtractionService;
import com.resumeanalyzer.support.TestAuth;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ResumeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeTextExtractionService textExtractionService;

    @Value("${resume.storage.directory:target/test-uploads}")
    private String storageDirectory;

    @Autowired
    private ObjectMapper objectMapper;

    /** Bearer token of the user registered for this test. */
    private String token;

    @BeforeEach
    void registerAuthenticatedUser() throws Exception {
        token = TestAuth.registerAndGetToken(mockMvc, objectMapper, "Resume Test User",
                TestAuth.uniqueEmail("resume.test"));
    }

    /**
     * Resume endpoints require authentication. Rather than repeating the header on
     * every request, all requests in this class go through this helper.
     */
    private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(
                builder.header(TestAuth.authorizationHeader(token), TestAuth.bearer(token)));
    }

    // -----------------------------------------------------------------------
    // Helpers: programmatically create minimal test PDF and DOCX in memory
    // No real personal resumes are used.
    // -----------------------------------------------------------------------

    private byte[] buildMinimalPdf() throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(60, 700);
                content.showText("John Doe - Software Engineer");
                content.newLine();
                content.showText("Skills: Java, Spring Boot, SQL, REST APIs");
                content.newLine();
                content.showText("Experience: 3 years backend development at Acme Corp.");
                content.newLine();
                content.showText("Education: B.Tech Computer Science, 2020");
                content.endText();
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] buildMinimalDocx() throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            XWPFParagraph p1 = doc.createParagraph();
            XWPFRun run1 = p1.createRun();
            run1.setText("Jane Smith - Full Stack Developer");

            XWPFParagraph p2 = doc.createParagraph();
            XWPFRun run2 = p2.createRun();
            run2.setText("Skills: React, Node.js, MongoDB, Docker, Kubernetes");

            XWPFParagraph p3 = doc.createParagraph();
            XWPFRun run3 = p3.createRun();
            run3.setText("Experience: 4 years at Tech Solutions Inc, building microservices.");

            XWPFParagraph p4 = doc.createParagraph();
            XWPFRun run4 = p4.createRun();
            run4.setText("Education: B.E. Information Technology, 2019.");

            doc.write(baos);
            return baos.toByteArray();
        }
    }

    // -----------------------------------------------------------------------
    // Test 1: Valid PDF upload → 201 Created, PARSED status, no internal path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T01: Valid PDF upload returns 201 with PARSED status and safe metadata")
    void testUploadValidPdf() throws Exception {
        byte[] pdfBytes = buildMinimalPdf();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test_resume.pdf", "application/pdf", pdfBytes);

        perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.originalFileName", is("test_resume.pdf")))
                .andExpect(jsonPath("$.fileType", is("PDF")))
                .andExpect(jsonPath("$.fileSize", is((int) pdfBytes.length)))
                .andExpect(jsonPath("$.uploadStatus", is("PARSED")))
                .andExpect(jsonPath("$.extractedCharacterCount", greaterThan(0)))
                // Security: must not expose internal paths or extracted text in upload response
                .andExpect(jsonPath("$.storagePath").doesNotExist())
                .andExpect(jsonPath("$.extractedText").doesNotExist());
    }

    // -----------------------------------------------------------------------
    // Test 2: Valid DOCX upload → 201 Created, PARSED status
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T16: the committed scripts/test-resume.docx fixture is a parseable Word document")
    void testRealDocxFixtureParses() throws Exception {
        // Guards the fixture the end-to-end script and the manual review both rely on.
        // Resolved from the Maven module directory, which is where surefire runs.
        Path fixture = Paths.get("..", "..", "scripts", "test-resume.docx").normalize();
        assumeTrue(Files.exists(fixture), "scripts/test-resume.docx fixture is not present");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                Files.readAllBytes(fixture));

        String body = perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadStatus", is("PARSED")))
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(body);
        assertThat(node.get("extractedCharacterCount").asInt())
                .as("the fixture must yield real text")
                .isGreaterThan(200);

        long id = node.get("id").asLong();
        String detail = perform(get("/api/resumes/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String extracted = objectMapper.readTree(detail).get("extractedText").asText();
        assertThat(extracted).contains("Asha Sharma");
        assertThat(extracted).contains("Spring Boot");
        assertThat(extracted).contains("Technical Skills");
    }

    @Test
    @DisplayName("T02: Valid DOCX upload returns 201 with PARSED status")
    void testUploadValidDocx() throws Exception {
        byte[] docxBytes = buildMinimalDocx();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test_resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes);

        perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFileName", is("test_resume.docx")))
                .andExpect(jsonPath("$.fileType", is("DOCX")))
                .andExpect(jsonPath("$.uploadStatus", is("PARSED")))
                .andExpect(jsonPath("$.extractedCharacterCount", greaterThan(0)));
    }

    // -----------------------------------------------------------------------
    // Test 3: Unsupported file type (TXT) → 400 Bad Request
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T03: Unsupported file type (TXT) returns 400 with appropriate message")
    void testUploadUnsupportedFileType() throws Exception {
        byte[] content = "This is a plain text file.".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/plain", content);

        perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("PDF and DOCX")));
    }

    // -----------------------------------------------------------------------
    // Test 4: File exceeding 10 MB → 400 Bad Request
    // (Using MockMultipartFile with large array — caught by validation service)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T04: File larger than 10 MB returns 400")
    void testUploadFileTooLarge() throws Exception {
        // 11 MB of zeros — validation service checks size before extraction
        byte[] largeContent = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large_resume.pdf", "application/pdf", largeContent);

        perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("10 MB")));
    }

    // -----------------------------------------------------------------------
    // Test 5: Empty file → 400 Bad Request
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T05: Empty file returns 400 with 'file is empty' message")
    void testUploadEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("empty")));
    }

    // -----------------------------------------------------------------------
    // Test 6: PDF text extraction unit test (service directly)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T06: PDF text extraction service extracts meaningful text")
    void testPdfTextExtraction() throws Exception {
        byte[] pdfBytes = buildMinimalPdf();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", pdfBytes);

        String extractedText = textExtractionService.extractText(file, "PDF");

        assertThat(extractedText).isNotBlank();
        assertThat(extractedText.length()).isGreaterThanOrEqualTo(50);
        assertThat(extractedText).containsIgnoringCase("John Doe");
        assertThat(extractedText).containsIgnoringCase("Java");
        // Must not have 3+ consecutive newlines (normalization applied)
        assertThat(extractedText).doesNotContainPattern("\\n{3,}");
    }

    // -----------------------------------------------------------------------
    // Test 7: DOCX text extraction unit test (service directly)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T07: DOCX text extraction service extracts meaningful text")
    void testDocxTextExtraction() throws Exception {
        byte[] docxBytes = buildMinimalDocx();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes);

        String extractedText = textExtractionService.extractText(file, "DOCX");

        assertThat(extractedText).isNotBlank();
        assertThat(extractedText.length()).isGreaterThanOrEqualTo(50);
        assertThat(extractedText).containsIgnoringCase("Jane Smith");
        assertThat(extractedText).containsIgnoringCase("React");
    }

    // -----------------------------------------------------------------------
    // Test 8: Resume metadata persisted correctly in DB
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T08: Resume metadata is persisted correctly after upload")
    void testResumeMetadataPersistence() throws Exception {
        byte[] pdfBytes = buildMinimalPdf();
        MockMultipartFile file = new MockMultipartFile(
                "file", "persisted_resume.pdf", "application/pdf", pdfBytes);

        perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isCreated());

        // Verify DB record
        var resumes = resumeRepository.findAll();
        assertThat(resumes).hasSize(1);
        var saved = resumes.get(0);
        assertThat(saved.getOriginalFileName()).isEqualTo("persisted_resume.pdf");
        assertThat(saved.getFileType()).isEqualTo("PDF");
        assertThat(saved.getUploadStatus()).isEqualTo(UploadStatus.PARSED);
        assertThat(saved.getExtractedText()).isNotBlank();
        assertThat(saved.getStoredFileName()).isNotBlank();
        // Stored filename must be UUID-based, NOT the original name
        assertThat(saved.getStoredFileName()).doesNotContain("persisted_resume");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    // -----------------------------------------------------------------------
    // Test 9: List resumes API (GET /api/resumes)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T09: GET /api/resumes returns list of uploaded resume metadata")
    void testListResumes() throws Exception {
        // Upload a PDF first
        byte[] pdfBytes = buildMinimalPdf();
        perform(multipart("/api/resumes/upload")
                        .file(new MockMultipartFile("file", "list_test.pdf",
                                "application/pdf", pdfBytes)))
                .andExpect(status().isCreated());

        perform(get("/api/resumes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].originalFileName", is("list_test.pdf")))
                .andExpect(jsonPath("$[0].uploadStatus", is("PARSED")))
                // List response must NOT include extractedText
                .andExpect(jsonPath("$[0].extractedText").doesNotExist());
    }

    // -----------------------------------------------------------------------
    // Test 10: GET /api/resumes/{id} returns detail with extractedText
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T10: GET /api/resumes/{id} returns detail response with extracted text")
    void testGetResumeById() throws Exception {
        byte[] pdfBytes = buildMinimalPdf();
        String responseBody = perform(multipart("/api/resumes/upload")
                        .file(new MockMultipartFile("file", "detail_test.pdf",
                                "application/pdf", pdfBytes)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract the id from the upload response
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules(); // handles LocalDateTime
        var node = mapper.readTree(responseBody);
        long id = node.get("id").asLong();

        perform(get("/api/resumes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) id)))
                .andExpect(jsonPath("$.extractedText", notNullValue()))
                .andExpect(jsonPath("$.extractedText", not(emptyString())))
                // Must NOT expose storage path
                .andExpect(jsonPath("$.storagePath").doesNotExist());
    }

    // -----------------------------------------------------------------------
    // Test 11: GET /api/resumes/{id} — not found → 404
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T11: GET /api/resumes/{id} with non-existent id returns 404")
    void testGetResumeByIdNotFound() throws Exception {
        perform(get("/api/resumes/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("999999")));
    }

    // -----------------------------------------------------------------------
    // Test 12: DELETE /api/resumes/{id} removes DB record and cleans up file
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T12: DELETE /api/resumes/{id} returns 204 and removes record from DB")
    void testDeleteResume() throws Exception {
        byte[] pdfBytes = buildMinimalPdf();
        String responseBody = perform(multipart("/api/resumes/upload")
                        .file(new MockMultipartFile("file", "delete_test.pdf",
                                "application/pdf", pdfBytes)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        var node = mapper.readTree(responseBody);
        long id = node.get("id").asLong();

        // Get stored filename before delete
        var resumeBeforeDelete = resumeRepository.findById(id);
        assertThat(resumeBeforeDelete).isPresent();
        String storedFileName = resumeBeforeDelete.get().getStoredFileName();

        // Delete
        perform(delete("/api/resumes/{id}", id))
                .andExpect(status().isNoContent());

        // DB record gone
        assertThat(resumeRepository.findById(id)).isEmpty();

        // File on disk should be gone
        Path storedFile = Paths.get(storageDirectory).resolve(storedFileName);
        assertThat(Files.exists(storedFile)).isFalse();
    }

    // -----------------------------------------------------------------------
    // Test 13: DELETE non-existent resume → 404
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T13: DELETE /api/resumes/{id} with non-existent id returns 404")
    void testDeleteResumeNotFound() throws Exception {
        perform(delete("/api/resumes/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    // -----------------------------------------------------------------------
    // Test 14: Upload response must not contain internal filesystem path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T14: Upload response must not expose internal storage path or filesystem details")
    void testUploadResponseDoesNotLeakInternalPaths() throws Exception {
        byte[] pdfBytes = buildMinimalPdf();
        MockMultipartFile file = new MockMultipartFile(
                "file", "privacy_test.pdf", "application/pdf", pdfBytes);

        String response = perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("storagePath");
        assertThat(response).doesNotContain("\\uploads\\");
        assertThat(response).doesNotContain("/uploads/");
        assertThat(response).doesNotContain("target/test-uploads");
        assertThat(response).doesNotContain("extractedText");
    }

    // -----------------------------------------------------------------------
    // Test 15: Two uploads → both appear in list
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T15: Two uploaded resumes both appear in GET /api/resumes")
    void testMultipleUploadsAppearInList() throws Exception {
        byte[] pdfBytes = buildMinimalPdf();
        byte[] docxBytes = buildMinimalDocx();

        perform(multipart("/api/resumes/upload")
                        .file(new MockMultipartFile("file", "first.pdf", "application/pdf", pdfBytes)))
                .andExpect(status().isCreated());

        perform(multipart("/api/resumes/upload")
                        .file(new MockMultipartFile("file", "second.docx",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                docxBytes)))
                .andExpect(status().isCreated());

        perform(get("/api/resumes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
