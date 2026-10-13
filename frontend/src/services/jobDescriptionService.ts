import { request } from './api';
import type { CreateJobDescriptionRequest, JobDescription } from '../types/api';

export const jobDescriptionService = {
  /** POST /api/job-descriptions */
  create(payload: CreateJobDescriptionRequest): Promise<JobDescription> {
    return request<JobDescription>('/api/job-descriptions', { method: 'POST', body: payload });
  },

  /** GET /api/job-descriptions */
  list(): Promise<JobDescription[]> {
    return request<JobDescription[]>('/api/job-descriptions');
  },

  /** GET /api/job-descriptions/{id} */
  getById(id: number): Promise<JobDescription> {
    return request<JobDescription>(`/api/job-descriptions/${id}`);
  },

  /** DELETE /api/job-descriptions/{id} — 204 No Content. */
  remove(id: number): Promise<void> {
    return request<void>(`/api/job-descriptions/${id}`, { method: 'DELETE' });
  },
};
