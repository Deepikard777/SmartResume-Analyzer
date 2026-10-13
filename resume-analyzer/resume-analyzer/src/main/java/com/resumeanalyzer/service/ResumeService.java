package com.resumeanalyzer.service;

import com.resumeanalyzer.dto.ResumeDetailResponse;
import com.resumeanalyzer.dto.ResumeResponse;
import com.resumeanalyzer.entity.Resume;
import com.resumeanalyzer.entity.UploadStatus;
import com.resumeanalyzer.entity.User;
import com.resumeanalyzer.exception.FileStorageException;
import com.resumeanalyzer.exception.InvalidCredentialsException;
import com.resumeanalyzer.exception.ResumeNotFoundException;
import com.resumeanalyzer.exception.ResumeParsingException;
import com.resumeanalyzer.repository.ResumeRepository;
import com.resumeanalyzer.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Upload, read and delete resumes.
 *
 * Every operation is scoped to the authenticated user id passed in by the
 * controller. A resume owned by somebody else is reported as "not found" (404)
 * rather than "forbidden" (403), so the API never reveals which ids exist.
 */
@Service
@Transactional(readOnly = true)
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final ResumeFileValidationService validationService;
    private final FileStorageService fileStorageService;
    private final ResumeTextExtractionService textExtractionService;
    private final String storageDirectory;

    public ResumeService(
            ResumeRepository resumeRepository,
            UserRepository userRepository,
            ResumeFileValidationService validationService,
            FileStorageService fileStorageService,
            ResumeTextExtractionService textExtractionService,
            @Value("${resume.storage.directory:./uploads/resumes}") String storageDirectory) {
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.validationService = validationService;
        this.fileStorageService = fileStorageService;
        this.textExtractionService = textExtractionService;
        this.storageDirectory = storageDirectory;
    }

    @Transactional
    public ResumeResponse uploadResume(MultipartFile file, Long ownerId) {
        User owner = loadOwner(ownerId);

        // Step 1: Validate
        validationService.validate(file);
        String extension = validationService.extractExtension(file.getOriginalFilename());
        String fileType = extension.toUpperCase();

        // Step 2: Store the file (UUID-named)
        String storedFileName = fileStorageService.storeFile(file, extension);
        String storagePath = storageDirectory + "/" + storedFileName;

        // Step 3: Extract text — on failure, record FAILED status and re-throw
        String extractedText;
        try {
            extractedText = textExtractionService.extractText(file, fileType);
        } catch (ResumeParsingException e) {
            // Save a FAILED entity so the upload attempt is traceable
            Resume failedResume = Resume.builder()
                    .originalFileName(file.getOriginalFilename())
                    .storedFileName(storedFileName)
                    .fileType(fileType)
                    .fileSize(file.getSize())
                    .storagePath(storagePath)
                    .extractedText(null)
                    .uploadStatus(UploadStatus.FAILED)
                    .user(owner)
                    .build();
            resumeRepository.save(failedResume);
            // Remove the stored file since parsing failed — don't keep unusable data
            try {
                fileStorageService.deleteFile(storedFileName);
            } catch (FileStorageException fse) {
                log.warn("Could not delete stored file after parse failure: {}", storedFileName, fse);
            }
            throw e;
        } catch (Exception e) {
            log.error("Unexpected failure during text extraction for stored file: {}", storedFileName, e);
            try {
                fileStorageService.deleteFile(storedFileName);
            } catch (FileStorageException fse) {
                log.warn("Could not delete stored file after unexpected failure: {}", storedFileName, fse);
            }
            throw new FileStorageException("An unexpected error occurred while processing the resume.");
        }

        // Step 4: Persist the successfully parsed resume, owned by the caller
        Resume resume = Resume.builder()
                .originalFileName(file.getOriginalFilename())
                .storedFileName(storedFileName)
                .fileType(fileType)
                .fileSize(file.getSize())
                .storagePath(storagePath)
                .extractedText(extractedText)
                .uploadStatus(UploadStatus.PARSED)
                .user(owner)
                .build();

        resume = resumeRepository.save(resume);
        log.info("Resume uploaded and parsed successfully: id={}, ownerId={}, type={}, chars={}",
                resume.getId(), ownerId, fileType, extractedText.length());

        return toResumeResponse(resume);
    }

    public List<ResumeResponse> getAllResumes(Long ownerId) {
        return resumeRepository.findByUserIdOrderByCreatedAtDesc(ownerId)
                .stream()
                .map(this::toResumeResponse)
                .collect(Collectors.toList());
    }

    public ResumeDetailResponse getResumeById(Long id, Long ownerId) {
        Resume resume = findOwnedResume(id, ownerId);
        return toResumeDetailResponse(resume);
    }

    /** Resolve a resume that is guaranteed to belong to the given user. */
    public Resume findOwnedResume(Long id, Long ownerId) {
        return resumeRepository.findByIdAndUserId(id, ownerId)
                .orElseThrow(() -> new ResumeNotFoundException("Resume not found with id: " + id));
    }

    @Transactional
    public void deleteResume(Long id, Long ownerId) {
        Resume resume = findOwnedResume(id, ownerId);

        // Attempt to delete from disk; log but do not throw if file is already gone
        try {
            fileStorageService.deleteFile(resume.getStoredFileName());
        } catch (FileStorageException e) {
            log.warn("Could not delete file from disk for resume id={}: {}", id, e.getMessage());
        }

        resumeRepository.delete(resume);
        log.info("Resume deleted: id={}, ownerId={}", id, ownerId);
    }

    private User loadOwner(Long ownerId) {
        return userRepository.findById(ownerId)
                .orElseThrow(() -> new InvalidCredentialsException(
                        "The account for this token no longer exists."));
    }

    // --- Mapping helpers ---

    private ResumeResponse toResumeResponse(Resume resume) {
        return ResumeResponse.builder()
                .id(resume.getId())
                .originalFileName(resume.getOriginalFileName())
                .fileType(resume.getFileType())
                .fileSize(resume.getFileSize())
                .uploadStatus(resume.getUploadStatus())
                .extractedCharacterCount(
                        resume.getExtractedText() != null ? resume.getExtractedText().length() : null)
                .createdAt(resume.getCreatedAt())
                .updatedAt(resume.getUpdatedAt())
                .build();
    }

    private ResumeDetailResponse toResumeDetailResponse(Resume resume) {
        return ResumeDetailResponse.builder()
                .id(resume.getId())
                .originalFileName(resume.getOriginalFileName())
                .fileType(resume.getFileType())
                .fileSize(resume.getFileSize())
                .uploadStatus(resume.getUploadStatus())
                .extractedText(resume.getExtractedText())
                .extractedCharacterCount(
                        resume.getExtractedText() != null ? resume.getExtractedText().length() : null)
                .createdAt(resume.getCreatedAt())
                .updatedAt(resume.getUpdatedAt())
                .build();
    }
}
