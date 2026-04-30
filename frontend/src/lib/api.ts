import {
  CreateUploadResponse,
  UploadStatusResponse,
  OverviewResponse,
  MetricSeriesResponse,
  ChatMessageRequest,
  ChatMessageResponse,
  ChatSessionResponse,
} from './types';

export interface LoginRequest { username: string; password: string; }
export interface RegisterRequest { username: string; email: string; password: string; }
export interface AuthResponse { token: string; tokenType: string; userId: string; username: string; role: string; }

function getJwtHeader(): Record<string, string> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('hp-auth-token') : null;
  return token ? { 'Authorization': `Bearer ${token}` } : {};
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...getJwtHeader(),
      ...((options?.headers as Record<string, string>) || {}),
    },
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(err || `HTTP ${res.status}`);
  }
  return res.json();
}

const BACKEND = 'http://127.0.0.1:8080';

interface UploadProgress {
  loaded: number;
  total: number;
  percent: number;
}

export async function createUploadWithProgress(
  file: File,
  onProgress: (p: UploadProgress) => void
): Promise<CreateUploadResponse> {
  return new Promise((resolve, reject) => {
    const form = new FormData();
    form.append('file', file);
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${BACKEND}/api/v1/uploads`);
    const token = typeof window !== 'undefined' ? localStorage.getItem('hp-auth-token') : null;
    if (token) xhr.setRequestHeader('Authorization', 'Bearer ' + token);
    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable) {
        onProgress({ loaded: e.loaded, total: e.total, percent: Math.round((e.loaded / e.total) * 100) });
      }
    });
    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try { resolve(JSON.parse(xhr.responseText)); } catch { reject(new Error('Invalid response')); }
      } else {
        reject(new Error(xhr.responseText || `HTTP ${xhr.status}`));
      }
    });
    xhr.addEventListener('error', () => reject(new Error('上传失败，请检查网络')));
    xhr.send(form);
  });
}

export async function loadDemo(): Promise<CreateUploadResponse> {
  return request('/api/v1/demo', { method: 'POST' });
}

export async function completeUpload(uploadId: string): Promise<UploadStatusResponse> {
  return request(`/api/v1/uploads/${uploadId}/complete`, { method: 'POST' });
}

export async function getUploadStatus(uploadId: string): Promise<UploadStatusResponse> {
  return request(`/api/v1/uploads/${uploadId}`);
}

export async function deleteUpload(uploadId: string): Promise<{ status: string }> {
  return request(`/api/v1/uploads/${uploadId}`, { method: 'DELETE' });
}

export async function getOverview(reportId: string): Promise<OverviewResponse> {
  return request(`/api/v1/reports/${reportId}/overview`);
}

export async function getMetricSeries(
  reportId: string,
  metricKey: string,
  granularity: string = 'DAILY'
): Promise<MetricSeriesResponse> {
  return request(
    `/api/v1/reports/${reportId}/metrics/${metricKey}?granularity=${granularity}`
  );
}

export async function getDualMetricSeries(
  reportId: string,
  metricKey1: string,
  metricKey2: string,
  granularity: string = 'DAILY'
): Promise<MetricSeriesResponse> {
  return request(
    `/api/v1/reports/${reportId}/metrics/${metricKey1},${metricKey2}?granularity=${granularity}`
  );
}

export async function listChatSessions(): Promise<ChatSessionResponse[]> {
  return request('/api/v1/chat/sessions');
}

export async function getChatSession(sessionId: string): Promise<ChatSessionResponse> {
  return request(`/api/v1/chat/sessions/${sessionId}`);
}

export async function getInsight(reportId: string, type: string = 'weekly'): Promise<{
  type: string; startDate: string; endDate: string;
  stateDistribution: Record<string, number>;
  highlights: { metricKey: string; latest: number; weeklyAvg: number; priorAvg: number; changePct: number; trend: string }[];
  aiNarrative: string;
}> {
  return request(`/api/v1/reports/${reportId}/insight?type=${type}`);
}

export async function sendChatMessage(
  sessionId: string,
  data: ChatMessageRequest
): Promise<ChatMessageResponse> {
  return request(`/api/v1/chat/sessions/${sessionId}/messages`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

// ── Auth ──

export async function login(data: LoginRequest): Promise<AuthResponse> {
  return request('/api/v1/auth/login', { method: 'POST', body: JSON.stringify(data) });
}

export async function register(data: RegisterRequest): Promise<AuthResponse> {
  return request('/api/v1/auth/register', { method: 'POST', body: JSON.stringify(data) });
}
