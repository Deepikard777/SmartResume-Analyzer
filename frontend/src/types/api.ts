/**
 * TypeScript mirrors of the existing Spring Boot DTOs.
 * Field names and nullability are taken directly from the Java records/classes
 * in com.resumeanalyzer.dto so that no field is invented or renamed.
 */

/** com.resumeanalyzer.entity.UploadStatus */
export type UploadStatus = 'UPLOADED' | 'PROCESSING' | 'PARSED' | 'FAILED';

/** com.resumeanalyzer.entity.AnalysisStatus */
export type AnalysisStatus = 'COMPLETED' | 'FAILED';

/** com.resumeanalyzer.dto.UserResponse */
export interface User {
  id: number;
  name: string;
  email: string;
}

/** com.resumeanalyzer.dto.AuthResponse */
export interface AuthResponse {
  message: string;
  user: User;
  /** Signed HS256 JWT. Send as `Authorization: Bearer <token>`. */
  token: string;
  tokenType: string;
  expiresInMs: number;
}

/** com.resumeanalyzer.dto.ResumeResponse */
export interface Resume {
  id: number;
  originalFileName: string;
  fileType: string;
  fileSize: number;
  uploadStatus: UploadStatus;
  extractedCharacterCount: number | null;
  createdAt: string;
  updatedAt: string;
}

/** com.resumeanalyzer.dto.ResumeDetailResponse */
export interface ResumeDetail extends Resume {
  extractedText: string | null;
}

/** com.resumeanalyzer.dto.JobDescriptionResponse */
export interface JobDescription {
  id: number;
  jobTitle: string;
  companyName: string | null;
  descriptionText: string;
  createdAt: string;
  updatedAt: string;
}

/** com.resumeanalyzer.dto.CreateJobDescriptionRequest */
export interface CreateJobDescriptionRequest {
  jobTitle: string;
  /** Optional in the backend (only @Size is applied), so null is accepted. */
  companyName: string | null;
  descriptionText: string;
}

/** com.resumeanalyzer.dto.AnalyzeResumeRequest */
export interface AnalyzeResumeRequest {
  resumeId: number;
  jobDescriptionId: number;
}

/** com.resumeanalyzer.dto.AnalysisResponse.ScoreBreakdown */
export interface ScoreBreakdown {
  skillMatch: number | null;
  keywordMatch: number | null;
  relevance: number | null;
  structure: number | null;
  formatting: number | null;
}

/** com.resumeanalyzer.dto.AnalysisResponse.FlaggedKeyword */
export interface FlaggedKeyword {
  keyword: string;
  frequency: number;
  reason: string;
}

/** com.resumeanalyzer.dto.AnalysisResponse.KeywordStuffingInfo */
export interface KeywordStuffingInfo {
  risk: string | null;
  flaggedKeywords: FlaggedKeyword[] | null;
}

/** com.resumeanalyzer.dto.AnalysisResponse.StructureCheck */
export interface StructureCheck {
  section: string;
  status: 'PASSED' | 'WARNING';
  message: string;
}

/** com.resumeanalyzer.dto.AnalysisResponse.Recommendation */
export interface Recommendation {
  category: string;
  /** HIGH | MEDIUM | LOW */
  priority: string;
  title: string;
  detail: string;
  relatedItems: string[] | null;
  /** "RULE" for the deterministic generator, "AI" for LLM-authored text. */
  source: string | null;
}

/** com.resumeanalyzer.dto.AnalysisResponse.KeywordCoverage */
export interface KeywordCoverage {
  keyword: string;
  /** Whole-word occurrences in the job description text. */
  jobDescriptionCount: number | null;
  /** Whole-word occurrences in the extracted resume text. */
  resumeCount: number | null;
  matched: boolean | null;
  /** True when the repetition detector flagged this keyword. */
  flagged: boolean | null;
  flagReason: string | null;
  inJobDescription: boolean | null;
}

/** com.resumeanalyzer.dto.AiStatusResponse */
export interface AiStatus {
  enabled: boolean;
  reason: string | null;
  provider: string | null;
  model: string | null;
  maxRecommendations: number | null;
  disclaimer: string | null;
}

/** com.resumeanalyzer.dto.AnalysisResponse */
export interface Analysis {
  id: number;
  resumeId: number;
  resumeFileName: string;
  jobDescriptionId: number;
  jobTitle: string;
  companyName: string | null;
  atsScore: number;
  scoreBreakdown: ScoreBreakdown;
  matchedSkills: string[] | null;
  missingSkills: string[] | null;
  optionalSkills: string[] | null;
  matchedKeywords: string[] | null;
  missingKeywords: string[] | null;
  keywordStuffing: KeywordStuffingInfo | null;
  /** Per-keyword frequency and coverage telemetry (powers the frequency view). */
  keywordCoverage: KeywordCoverage[] | null;
  structure: StructureCheck[] | null;
  /** Deterministic, actionable recommendations derived from the findings above. */
  recommendations: Recommendation[] | null;
  /** LLM-authored suggestions; present only after they have been requested. */
  aiRecommendations: Recommendation[] | null;
  aiGeneratedAt: string | null;
  aiProvider: string | null;
  aiModel: string | null;
  createdAt: string;
}

/** com.resumeanalyzer.dto.AnalysisSummaryResponse */
export interface AnalysisSummary {
  id: number;
  resumeId: number;
  resumeFileName: string;
  jobDescriptionId: number;
  jobTitle: string;
  companyName: string | null;
  atsScore: number;
  status: AnalysisStatus;
  createdAt: string;
}

/** com.resumeanalyzer.dto.ErrorResponse */
export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  validationErrors?: Record<string, string>;
}
