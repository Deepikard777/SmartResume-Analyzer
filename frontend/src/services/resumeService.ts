import { request } from './api';
import type { Resume, ResumeDetail } from '../types/api';

export const resumeService = {
  /** POST /api/resumes/upload — multipart/form-data with a single `file` part. */
  upload(file: File): Promise<Resume> {
    const formData = new FormData();
    formData.append('file', file);
    return request<Resume>('/api/resumes/upload', { method: 'POST', formData });
  },

  /** GET /api/resumes — metadata only, no extractedText. */
  list(): Promise<Resume[]> {
    return request<Resume[]>('/api/resumes');
  },

  /** GET /api/resumes/{id} — includes extractedText. */
  getById(id: number): Promise<ResumeDetail> {
    return request<ResumeDetail>(`/api/resumes/${id}`);
  },

  /** DELETE /api/resumes/{id} — 204 No Content. */
  remove(id: number): Promise<void> {
    return request<void>(`/api/resumes/${id}`, { method: 'DELETE' });
  },
};
