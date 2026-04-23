// Same key the platform marketplace writes (web/src/api/client.ts), so
// a user signed in at stage.neomud.app is already signed in at
// stage.neomud.app/maker — no token handoff needed.
const TOKEN_KEY = 'neomud_access_token';

// Absolute path the maker uses for API calls. Defaults to /api so the
// Vite middleware dev setup keeps working untouched. Staging builds set
// VITE_API_BASE=/maker-api so requests go through Caddy, which rewrites
// /maker-api/* back to /api/* before forwarding to this same server.
const API_BASE = (import.meta.env.VITE_API_BASE ?? '/api').replace(/\/$/, '');

/**
 * Called when the server rejects a request with 401. Clears the local
 * token so future requests don't reuse a stale one, then sends the
 * browser to the platform root so the user can sign in again.
 * Exported so tests can replace or spy.
 */
export function onUnauthorized() {
  localStorage.removeItem(TOKEN_KEY);
  window.location.assign('/');
}

/** Current project scope — when set, API calls are prefixed with /projects/{name} */
let currentProject: string | null = null;

export function setProjectScope(name: string | null) {
  currentProject = name;
}

export function getProjectScope(): string | null {
  return currentProject;
}

function getAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {};
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

export function resolveUrl(path: string): string {
  // Project-scoped paths: prefix with /projects/{name}
  if (currentProject && !path.startsWith('/projects')) {
    return `${API_BASE}/projects/${encodeURIComponent(currentProject)}${path}`;
  }
  return `${API_BASE}${path}`;
}

/**
 * Absolute URL to a resource served by the maker API, for non-fetch
 * consumers (Audio/Image src, anchor hrefs). Pass a server-relative path
 * starting with a slash, e.g. "/assets/images/npc.webp".
 */
export function assetUrl(path: string): string {
  return `${API_BASE}${path}`;
}

function extractErrorMessage(text: string, status: number): string {
  try {
    const json = JSON.parse(text);
    if (json.error) return json.error;
  } catch { /* not JSON, use raw text */ }
  if (status === 401) return 'Session expired. Please log in again.';
  if (status >= 500) return 'Something went wrong. Please try again.';
  return text || `Request failed (${status})`;
}

function parseJsonBody(text: string): Record<string, unknown> | null {
  try {
    const parsed = JSON.parse(text);
    return typeof parsed === 'object' && parsed !== null ? parsed : null;
  } catch {
    return null;
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const opts: RequestInit = {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...getAuthHeaders(),
    },
  };
  if (body !== undefined) {
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(resolveUrl(path), opts);
  if (!res.ok) {
    if (res.status === 401) onUnauthorized();
    const text = await res.text().catch(() => res.statusText);
    const err = new Error(extractErrorMessage(text, res.status));
    (err as any).status = res.status;
    (err as any).body = parseJsonBody(text);
    throw err;
  }
  const contentType = res.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    return res.json() as Promise<T>;
  }
  return undefined as unknown as T;
}

async function uploadRequest<T>(path: string, file: File, fields?: Record<string, string>): Promise<T> {
  const formData = new FormData();
  formData.append('file', file);
  if (fields) {
    for (const [key, value] of Object.entries(fields)) {
      formData.append(key, value);
    }
  }
  const res = await fetch(resolveUrl(path), {
    method: 'POST',
    headers: getAuthHeaders(),
    body: formData,
  });
  if (!res.ok) {
    if (res.status === 401) onUnauthorized();
    const text = await res.text().catch(() => res.statusText);
    const err = new Error(extractErrorMessage(text, res.status));
    (err as any).status = res.status;
    (err as any).body = parseJsonBody(text);
    throw err;
  }
  const contentType = res.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    return res.json() as Promise<T>;
  }
  return undefined as unknown as T;
}

const api = {
  assetUrl(path: string): string {
    return assetUrl(path);
  },
  get<T = any>(path: string): Promise<T> {
    return request<T>('GET', path);
  },
  post<T = any>(path: string, body?: unknown): Promise<T> {
    return request<T>('POST', path, body);
  },
  put<T = any>(path: string, body?: unknown): Promise<T> {
    return request<T>('PUT', path, body);
  },
  del<T = any>(path: string): Promise<T> {
    return request<T>('DELETE', path);
  },
  upload<T = any>(path: string, file: File, fields?: Record<string, string>): Promise<T> {
    return uploadRequest<T>(path, file, fields);
  },

  /** Auth helpers */
  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  },
  setToken(token: string) {
    localStorage.setItem(TOKEN_KEY, token);
  },
  clearToken() {
    localStorage.removeItem(TOKEN_KEY);
  },
  isAuthenticated(): boolean {
    return !!localStorage.getItem(TOKEN_KEY);
  },
};

export default api;
