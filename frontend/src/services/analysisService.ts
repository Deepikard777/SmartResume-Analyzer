import { request, requestBlob } from './api';
import type { Analysis, AiStatus, AnalysisSummary, AnalyzeResumeRequest } from '../types/api';

export const analysisService = {
  /** POST /api/analyses — run the deterministic analysis and persist the result. */
  analyze(payload: AnalyzeResumeRequest): Promise<Analysis> {
    return request<Analysis>('/api/analyses', { method: 'POST', body: payload });
  },

  /** GET /api/analyses — the caller's history, newest first (lightweight summaries). */
  list(): Promise<AnalysisSummary[]> {
    return request<AnalysisSummary[]>('/api/analyses');
  },

  /** GET /api/analyses/{id} — the full stored diagnostic payload. */
  getById(id: number): Promise<Analysis> {
    return request<Analysis>(`/api/analyses/${id}`);
  },

  /** DELETE /api/analyses/{id} — remove one stored result. */
  remove(id: number): Promise<void> {
    return request<void>(`/api/analyses/${id}`, { method: 'DELETE' });
  },

  /**
   * GET /api/analyses/{id}/report — the analysis as a PDF.
   * Returns the blob and the server's suggested file name, without triggering a
   * download itself, so the caller decides when to save it.
   */
  async downloadReport(id: number) {
    const { blob, contentDisposition } = await requestBlob(`/api/analyses/${id}/report`);
    return { blob, contentDisposition };
  },

  /** GET /api/ai/status — whether the optional LLM feature is usable. */
  aiStatus(): Promise<AiStatus> {
    return request<AiStatus>('/api/ai/status');
  },

  /**
   * POST /api/analyses/{id}/ai-recommendations — ask the provider for suggestions.
   * Returns the refreshed analysis; answers 503 when no provider is configured, in
   * which case the deterministic recommendations remain available.
   */
  generateAiRecommendations(id: number): Promise<Analysis> {
    return request<Analysis>(`/api/analyses/${id}/ai-recommendations`, { method: 'POST' });
  },
};
