// Gọi API Spring Boot. API của Phi/Việt bọc {success, message, data}; API của Toàn trả JSON thô: client xử lý cả hai.

import { restoreVi } from './vi';

export const PROPERTY_ID = 1; // Hiện chỉ có một khu trọ; đổi khi Chi làm xong API khu trọ.

export class ApiError extends Error {
  constructor(message: string, public status: number) {
    super(message);
  }
}

export type Role = 'LANDLORD' | 'MANAGER' | 'CASHIER' | 'METER_READER' | 'TENANT';

export interface Session {
  token: string;
  userId: number;
  name: string;
  email: string;
  roles: Role[];
  tenantId: number | null;
}

const KEY = 'stayly.session';
let session: Session | null = null;

function b64(s: string): string {
  const pad = s.replace(/-/g, '+').replace(/_/g, '/');
  return decodeURIComponent(
    atob(pad + '='.repeat((4 - (pad.length % 4)) % 4))
      .split('')
      .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
      .join(''),
  );
}

function fromToken(token: string): Session {
  const p = JSON.parse(b64(token.split('.')[1]));
  return {
    token,
    userId: Number(p.sub),
    name: p.name ?? p.email ?? 'Người dùng',
    email: p.email ?? '',
    roles: (p.roles ?? []) as Role[],
    tenantId: p.tenantId ?? null,
  };
}

export function loadSession(): Session | null {
  if (session) return session;
  try {
    const t = sessionStorage.getItem(KEY);
    if (t) {
      const s = fromToken(t);
      const exp = JSON.parse(b64(t.split('.')[1])).exp as number | undefined;
      if (!exp || exp * 1000 > Date.now()) session = s;
      else sessionStorage.removeItem(KEY);
    }
  } catch {
    /* sessionStorage có thể bị chặn: coi như chưa đăng nhập */
  }
  return session;
}

export function logout(): void {
  session = null;
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    /* bỏ qua */
  }
  location.hash = '#/login';
}

export function isTenant(s: Session | null): boolean {
  return !!s && s.roles.includes('TENANT');
}

export function can(...roles: Role[]): boolean {
  const s = loadSession();
  return !!s && roles.some((r) => s.roles.includes(r));
}

export async function login(identifier: string, password: string): Promise<Session> {
  const r = await request<{ accessToken: string }>('POST', '/api/auth/login', { identifier, password }, false);
  session = fromToken(r.accessToken);
  try {
    sessionStorage.setItem(KEY, r.accessToken);
  } catch {
    /* bỏ qua */
  }
  return session;
}

async function request<T>(method: string, path: string, body?: unknown, auth = true, form?: FormData): Promise<T> {
  const headers: Record<string, string> = {};
  const s = loadSession();
  if (auth && s) headers.Authorization = `Bearer ${s.token}`;
  let payload: BodyInit | undefined;
  if (form) payload = form;
  else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  let res: Response;
  try {
    res = await fetch(path, { method, headers, body: payload });
  } catch {
    throw new ApiError('Không kết nối được máy chủ. Kiểm tra mạng hoặc xem backend đã chạy chưa.', 0);
  }
  if (res.status === 401 && auth) {
    logout();
    throw new ApiError('Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.', 401);
  }
  const type = res.headers.get('content-type') ?? '';
  if (!type.includes('json')) {
    if (!res.ok) throw new ApiError(`Lỗi ${res.status}`, res.status);
    return (await res.text()) as unknown as T;
  }
  const json = await res.json();
  if (!res.ok || json?.success === false) {
    throw new ApiError(json?.message ?? json?.error ?? `Lỗi ${res.status}`, res.status);
  }
  return (json && typeof json === 'object' && 'success' in json && 'data' in json ? json.data : json) as T;
}

export const api = {
  get: <T>(p: string) => request<T>('GET', p),
  post: <T>(p: string, b?: unknown) => request<T>('POST', p, b ?? {}),
  put: <T>(p: string, b?: unknown) => request<T>('PUT', p, b ?? {}),
  patch: <T>(p: string, b?: unknown) => request<T>('PATCH', p, b ?? {}),
  del: <T>(p: string) => request<T>('DELETE', p),
  upload: <T>(p: string, file: Blob, method: 'POST' | 'PUT' = 'POST') => {
    const f = new FormData();
    f.append('file', file, (file as File).name || 'file.png');
    return request<T>(method, p, undefined, true, f);
  },
  /** Tải file nhị phân (PDF, Excel) kèm token rồi mở/lưu. */
  async download(path: string, filename: string): Promise<void> {
    const s = loadSession();
    const res = await fetch(path, { headers: s ? { Authorization: `Bearer ${s.token}` } : {} });
    if (!res.ok) throw new ApiError(`Không tải được file (${res.status})`, res.status);
    const url = URL.createObjectURL(await res.blob());
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
  },
};

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function errText(e: unknown): string {
  return e instanceof Error ? restoreVi(e.message) : 'Có lỗi xảy ra, thử lại sau.';
}
