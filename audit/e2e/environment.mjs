export function localBase(defaultUrl = 'http://127.0.0.1:5178') {
  const url = new URL(process.env.BASE_URL ?? defaultUrl);
  if (!['http:', 'https:'].includes(url.protocol) ||
      !['127.0.0.1', 'localhost'].includes(url.hostname) ||
      url.username || url.password || url.pathname !== '/' || url.search || url.hash) {
    throw new Error('BASE_URL phải là URL gốc HTTP(S) trên 127.0.0.1 hoặc localhost');
  }
  return url.origin;
}

export function adminCredentials() {
  const identifier = process.env.E2E_ADMIN_IDENTIFIER;
  const password = process.env.E2E_ADMIN_PASSWORD;
  if (!identifier?.trim() || !password) {
    throw new Error('Đặt E2E_ADMIN_IDENTIFIER và E2E_ADMIN_PASSWORD cho tài khoản QA trước khi chạy E2E');
  }
  return { identifier, password };
}
