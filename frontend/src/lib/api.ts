// Gọi API Spring Boot. API của Phi/Việt bọc {success, message, data}; API của Toàn trả JSON thô: client xử lý cả hai.

import { restoreVi } from './vi';

const PROPERTY_KEY = 'stayly.property';
function savedProperty(): number {
  try {
    const v = Number(localStorage.getItem(PROPERTY_KEY));
    return v > 0 ? v : 1;
  } catch {
    return 1; // localStorage có thể bị chặn
  }
}
/** Khu trọ đang làm việc: chọn ở thanh trên, nhớ trong trình duyệt, đổi thì tải lại trang. */
export const PROPERTY_ID = savedProperty();
export function setProperty(id: number): void {
  try {
    localStorage.setItem(PROPERTY_KEY, String(id));
  } catch {
    /* không nhớ được thì lần sau về khu mặc định */
  }
}

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

// Hồ sơ đang đăng nhập: tên và ảnh nằm ngoài token nên phải tự giữ; đổi ở đâu thì thanh bên đổi ngay, không chờ đăng nhập lại.
let myAvatar: string | null = null;
const profileListeners = new Set<() => void>();
export const myAvatarUrl = (): string | null => myAvatar;
export function onMyProfile(fn: () => void): () => void {
  profileListeners.add(fn);
  return () => void profileListeners.delete(fn);
}
export function setMyProfile(p: { fullName?: string; avatarUrl?: string | null }): void {
  if (p.fullName && session) session.name = p.fullName;
  if (p.avatarUrl !== undefined) myAvatar = p.avatarUrl;
  profileListeners.forEach((fn) => fn());
}
/** Token chỉ mang tên lúc đăng nhập: nạp lại hồ sơ thật để tên/ảnh đúng cả sau khi tải lại trang. */
export async function refreshMyProfile(): Promise<void> {
  try {
    const p = await api.get<{ fullName: string; avatarUrl: string | null }>('/api/auth/profile');
    setMyProfile({ fullName: p.fullName, avatarUrl: p.avatarUrl });
  } catch { /* không nạp được thì giữ tên trong token */ }
}

function clearSession(): void {
  session = null;
  myAvatar = null;
  try {
    sessionStorage.removeItem(KEY);
    sessionStorage.removeItem(MUST_KEY);
    sessionStorage.removeItem(QR_KEY);
  } catch {
    /* bỏ qua */
  }
  location.hash = '#/login';
}

export async function logout(): Promise<void> {
  if (loadSession()) await request('POST', '/api/auth/logout');
  clearSession();
}

export function renewSession(accessToken: string): void {
  startSession({ accessToken });
  clearMustChangePassword();
}

export function isMeterReaderOnly(s: Session | null): boolean {
  return !!s && s.roles.includes('METER_READER') && !s.roles.some((r) => r === 'LANDLORD' || r === 'MANAGER' || r === 'CASHIER' || r === 'TENANT');
}

export function isTenant(s: Session | null): boolean {
  return !!s && s.roles.includes('TENANT');
}

export function can(...roles: Role[]): boolean {
  const s = loadSession();
  return !!s && roles.some((r) => s.roles.includes(r));
}

const MUST_KEY = 'stayly.mustChangePassword';
/** Đang dùng mật khẩu tạm (backend báo ở LoginResponse): giao diện bắt đổi trước khi làm việc. */
export function mustChangePassword(): boolean {
  try { return sessionStorage.getItem(MUST_KEY) === '1'; } catch { return false; }
}
export function clearMustChangePassword(): void {
  try { sessionStorage.removeItem(MUST_KEY); sessionStorage.removeItem(QR_KEY); } catch { /* bỏ qua */ }
}

const QR_KEY = 'stayly.viaQr';
/** Phiên này vào bằng mã QR: khách chưa biết mật khẩu tạm nên được đặt mật khẩu mới mà không cần nhập mật khẩu cũ. */
export function viaQrSession(): boolean {
  try { return sessionStorage.getItem(QR_KEY) === '1'; } catch { return false; }
}

export async function login(identifier: string, password: string): Promise<Session> {
  const s = startSession(await request<LoginResult>('POST', '/api/auth/login', { identifier, password }, false));
  try { sessionStorage.removeItem(QR_KEY); } catch { /* bỏ qua */ }
  return s;
}

/** Khách quét QR được cấp tài khoản: đổi mã trong QR lấy phiên đăng nhập (vẫn phải đổi mật khẩu tạm). */
export async function loginWithQr(token: string): Promise<Session> {
  const s = startSession(await request<LoginResult>('POST', '/api/auth/qr-login', { token }, false));
  try { sessionStorage.setItem(QR_KEY, '1'); } catch { /* bỏ qua */ }
  return s;
}

interface LoginResult { accessToken: string; mustChangePassword?: boolean }

function startSession(r: LoginResult): Session {
  session = fromToken(r.accessToken);
  try {
    if (r.mustChangePassword) sessionStorage.setItem(MUST_KEY, '1'); else sessionStorage.removeItem(MUST_KEY);
  } catch { /* bỏ qua */ }
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
    if (session?.token === s?.token) clearSession();
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
  /** Gửi nhiều tệp/trường một lần (ví dụ ảnh CCCD hai mặt). */
  uploadForm: <T>(p: string, f: FormData, method: 'POST' | 'PUT' = 'POST') => request<T>(method, p, undefined, true, f),
  /** Ảnh cần token (CCCD): tải về thành URL tạm để gắn vào <img>. */
  async blobUrl(path: string): Promise<string | null> {
    const s = loadSession();
    const res = await fetch(path, { headers: s ? { Authorization: `Bearer ${s.token}` } : {} });
    return res.ok ? URL.createObjectURL(await res.blob()) : null;
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

/** Tải ảnh riêng bằng phiên hiện tại và giải phóng URL tạm sau khi trình duyệt đọc xong. */
export function loadPrivateImage(img: HTMLImageElement, path: string): void {
  if (!path.startsWith('/api/') || new URL(path, location.origin).origin !== location.origin) return;
  void api.blobUrl(path).then((url) => {
    if (!url) return;
    const release = () => URL.revokeObjectURL(url);
    img.addEventListener('load', release, { once: true });
    img.addEventListener('error', release, { once: true });
    img.src = url;
  }).catch(() => {});
}

export function privateImage(path: string, alt: string, style = ''): HTMLImageElement {
  const img = document.createElement('img');
  img.alt = alt;
  img.style.cssText = style;
  loadPrivateImage(img, path);
  return img;
}

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
