// JSON over fetch with the bearer token; errors become ApiError with the backend's stable `code`.

export class ApiError extends Error {
  constructor(status, code, detail) {
    super(detail || code);
    this.status = status;
    this.code = code;
    this.detail = detail;
  }
}

const STATUS_CODES = { 401: 'UNAUTHORIZED', 403: 'FORBIDDEN', 404: 'NOT_FOUND', 0: 'NETWORK_ERROR' };

export function createApi(baseUrl, getToken) {
  async function request(method, path, body) {
    const headers = { Accept: 'application/json' };
    if (getToken) headers.Authorization = `Bearer ${await getToken()}`;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    let res;
    try {
      res = await fetch(baseUrl + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    } catch {
      throw new ApiError(0, 'NETWORK_ERROR', `Cannot reach ${baseUrl}. Is the service running?`);
    }
    const text = await res.text();
    let json = null;
    try { json = text ? JSON.parse(text) : null; } catch { json = null; }
    if (!res.ok) {
      const code = json?.code || STATUS_CODES[res.status] || `HTTP_${res.status}`;
      const detail = json?.detail || (res.status === 401
        ? (res.headers.get('WWW-Authenticate') || 'Your session is not valid for this service.')
        : `${method} ${path} failed (${res.status})`);
      throw new ApiError(res.status, code, detail);
    }
    return json;
  }
  return {
    get: (p) => request('GET', p),
    post: (p, b = {}) => request('POST', p, b),
    put: (p, b = {}) => request('PUT', p, b),
    del: (p) => request('DELETE', p),
  };
}
