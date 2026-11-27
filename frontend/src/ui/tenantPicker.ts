import { api } from '../lib/api';
import { remotePicker } from './kit';

interface TenantHit { tenantId: number; fullName: string; phoneNumber: string }

/** Ô chọn khách tìm trên server (tối đa 15 dòng mỗi lần). available=true: chỉ khách chưa có hợp đồng còn hiệu lực. */
export function tenantPicker(opts: { available?: boolean; exclude?: number; label?: string } = {}) {
  return remotePicker(async (q) => {
    const params = new URLSearchParams({ limit: '15' });
    if (q) params.set('keyword', q);
    if (opts.available) params.set('available', 'true');
    const hits = await api.get<TenantHit[]>(`/api/chi/tenants?${params}`);
    return hits.filter((t) => t.tenantId !== opts.exclude).map((t) => [String(t.tenantId), `${t.fullName} · ${t.phoneNumber}`] as [string, string]);
  }, { label: opts.label ?? 'Tìm khách hàng' });
}
