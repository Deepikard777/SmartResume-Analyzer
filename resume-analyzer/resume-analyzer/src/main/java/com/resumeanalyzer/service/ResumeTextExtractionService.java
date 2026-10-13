package com.resumeanalyzer.service;

import com.resumeanalyzer.exception.ResumeParsingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class ResumeTextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ResumeTextExtractionService.class);
    private static final int MINIMUM_MEANINGFUL_TEXT_LENGTH = 50;

    public String extractText(MultipartFile file, String fileType) {
        String rawText;
        try {
            rawText = switch (fileType.toUpperCase()) {
                case "PDF" -> extractFromPdf(file);
                case "DOCX" -> extractFromDocx(file);
                default -> throw new ResumeParsingException(
                        "Unsupported file type for text extraction: " + fileType);
            };
        } catch (ResumeParsingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error extracting text from {}", fileType, e);
            throw new ResumeParsingException(
                    "Failed to read the uploaded resume file. Ensure the file is not corrupted.");
        }

        return normalizeText(rawText, fileType);
    }

    private String extractFromPdf(MultipartFile file) throws IOException {
        // PDFBox 3.x API: use Loader.loadPDF() instead of PDDocument.load()
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (document.isEncrypted()) {
                throw new ResumeParsingException(
                        "The uploaded PDF is password-protected and cannot be read.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractFromDocx(MultipartFile file) throws IOException {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            try (XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                return extractor.getText();
            }
        }
    }

    private String normalizeText(String rawText, String fileType) {
        if (rawText == null) {
            rawText = "";
        }

        // Normalize line endings to \n
        String normalized = rawText
                .replace("\r\n", "\n")
                .replace("\r", "\n");

        // Collapse 3 or more consecutive newlines down to 2
        normalized = normalized.replaceAll("\n{3,}", "\n\n");

        // Trim leading/trailing whitespace
        normalized = normalized.trim();

        if (normalized.length() < MINIMUM_MEANINGFUL_TEXT_LENGTH) {
            throw new ResumeParsingException(
                    "Could not extract meaningful text from the uploaded resume. "
                            + "The " + fileType + " file may be image-based or empty.");
        }

        return normalized;
    }
}
