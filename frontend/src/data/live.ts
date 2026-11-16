// Phòng và khách thuê chưa có API của Chi, nên danh sách gốc lấy từ dữ liệu mẫu; trạng thái thuê và người đang ở
// được suy ra từ HỢP ĐỒNG THẬT (API hợp đồng), để màn Phòng, Khách thuê và Lập hợp đồng luôn khớp nhau.
import { api, Page } from '../lib/api';
import type { Contract } from './types';
import { SAMPLE_ROOMS, SAMPLE_TENANTS, SampleRoom, SampleTenant } from './sample';

export interface LiveRoom extends SampleRoom {
  contract?: Contract;
  tenantName?: string;
}
export interface LiveTenant extends SampleTenant {
  contract?: Contract;
}

export async function liveContracts(): Promise<Contract[]> {
  const [a, b] = await Promise.all([
    api.get<Page<Contract>>('/api/contracts?status=ACTIVE&size=100'),
    api.get<Page<Contract>>('/api/contracts?status=EXPIRING_SOON&size=100'),
  ]);
  return [...a.items, ...b.items];
}

export async function loadLive(): Promise<{ rooms: LiveRoom[]; tenants: LiveTenant[]; contracts: Contract[] }> {
  const contracts = await liveContracts().catch(() => [] as Contract[]);
  const byRoom = new Map(contracts.map((c) => [c.roomId, c]));
  const byTenant = new Map<number, Contract>();
  for (const c of contracts) if (!byTenant.has(c.tenantId)) byTenant.set(c.tenantId, c);
  const rooms: LiveRoom[] = SAMPLE_ROOMS.map((r) => {
    const c = byRoom.get(r.roomId);
    return {
      ...r,
      contract: c,
      tenantName: c?.tenantName,
      tenantId: c?.tenantId,
      status: c ? 'RENTED' : r.status === 'REPAIRING' ? 'REPAIRING' : 'VACANT',
    };
  });
  const tenants: LiveTenant[] = SAMPLE_TENANTS.map((t) => {
    const c = byTenant.get(t.tenantId);
    return { ...t, contract: c, roomCode: c?.roomCode, since: c?.startDate };
  });
  return { rooms, tenants, contracts };
}
