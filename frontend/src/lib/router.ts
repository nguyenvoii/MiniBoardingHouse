// Bộ định tuyến hash (#/duong-dan) nên Spring Boot chỉ cần phục vụ một file index.html.
export type Params = Record<string, string>;
export type RouteHandler = (view: HTMLElement, params: Params, query: URLSearchParams) => void | Promise<void>;

interface Route { pattern: RegExp; keys: string[]; handler: RouteHandler; area: 'staff' | 'tenant' | 'public' }
const routes: Route[] = [];

export function route(path: string, area: Route['area'], handler: RouteHandler): void {
  const keys: string[] = [];
  const pattern = new RegExp('^' + path.replace(/:([a-z]+)/gi, (_, k) => { keys.push(k); return '([^/]+)'; }) + '$');
  routes.push({ pattern, keys, handler, area });
}

export function current(): { path: string; query: URLSearchParams } {
  const raw = location.hash.replace(/^#/, '') || '/';
  const [path, qs] = raw.split('?');
  return { path: path || '/', query: new URLSearchParams(qs ?? '') };
}

export function navigate(path: string): void {
  location.hash = '#' + path;
}

export function match(path: string): { route: Route; params: Params } | null {
  for (const r of routes) {
    const m = r.pattern.exec(path);
    if (m) {
      const params: Params = {};
      r.keys.forEach((k, i) => (params[k] = decodeURIComponent(m[i + 1])));
      return { route: r, params };
    }
  }
  return null;
}
