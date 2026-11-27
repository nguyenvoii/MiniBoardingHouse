// Phòng, khách hàng và khu trọ lấy từ API của Chi (/api/chi/**). Trạng thái thuê và người đang ở được suy ra từ
// HỢP ĐỒNG THẬT (API hợp đồng), để màn Phòng, Khách hàng và Lập hợp đồng luôn khớp nhau.
import { api, Page, PROPERTY_ID } from '../lib/api';
import type { Contract } from './types';

export type RoomStatus = 'RENTED' | 'VACANT' | 'REPAIRING' | 'SUSPENDED' | 'CLEANING';

export interface RoomType {
  roomTypeId: number;
  propertyId: number;
  typeName: string;
  description: string | null;
  defaultRentPrice: number;
  defaultDepositAmount: number;
}

export interface LiveRoom {
  holdUntil?: string | null;
  roomTypeId?: number | null;
  roomTypeName?: string | null;
  initialElectricMeter?: number;
  initialWaterMeter?: number;
  roomId: number;
  code: string;
  floor: number;
  area: number;
  price: number;
  deposit: number;
  maxPeople: number;
  status: RoomStatus;
  tenantId?: number;
  amenities: string[];
  contract?: Contract;
  tenantName?: string;
}

export interface LiveTenant {
  tenantId: number;
  name: string;
  phone: string;
  idNumber: string;
  email?: string;
  job: string;
  roomCode?: string;
  since?: string;
  contract?: Contract;
}

export interface PropertyInfo {
  propertyId: number;
  propertyName: string;
  address: string;
  supportPhone: string | null;
  totalFloors: number | null;
  roomCount: number;
  floors: Array<{ floorId: number; floorNumber: number; floorName: string }>;
}

interface ApiRoom {
  holdUntil?: string | null;
  roomTypeId?: number | null; roomTypeName?: string | null;
  initialElectricMeter?: number; initialWaterMeter?: number;
  roomId: number; roomCode: string; floorNumber: number | null; areaSqm: number; baseRentPrice: number;
  depositAmount: number; maxOccupants: number; status: string;
}
export interface ApiTenant {
  tenantId: number; fullName: string; phoneNumber: string; identityNumber: string; email: string | null; occupation: string | null;
}
interface ApiFurnishing { roomId: number; itemName: string }

export async function liveContracts(): Promise<Contract[]> {
  // EXPIRED cũng tính: hết hạn mà chưa trả phòng/thanh lý thì khách vẫn ở, phòng chưa Trống
  // backend chặn 100 dòng mỗi trang: đọc hết các trang để phòng thứ 101 trở đi không hiện nhầm "Trống"
  const all = async (status: string): Promise<Contract[]> => {
    const first = await api.get<Page<Contract>>(`/api/contracts?status=${status}&size=100&page=0`);
    const out = [...first.items];
    for (let p = 1; p < first.totalPages && p < 20; p++) out.push(...(await api.get<Page<Contract>>(`/api/contracts?status=${status}&size=100&page=${p}`)).items);
    return out;
  };
  const [a, b, c] = await Promise.all([all('ACTIVE'), all('EXPIRING_SOON'), all('EXPIRED').catch(() => [] as Contract[])]);
  return [...a, ...b, ...c];
}

export function loadRoomTypes(): Promise<RoomType[]> {
  return api.get<RoomType[]>('/api/chi/room-types?propertyId=' + PROPERTY_ID);
}

export function loadProperties(): Promise<PropertyInfo[]> {
  return api.get<PropertyInfo[]>('/api/chi/properties');
}

function roomStatus(apiStatus: string, hasContract: boolean): RoomStatus {
  if (hasContract) return 'RENTED';
  if (apiStatus === 'REPAIRING' || apiStatus === 'SUSPENDED' || apiStatus === 'CLEANING') return apiStatus;
  return 'VACANT'; // kể cả khi DB ghi RENTED mà không còn hợp đồng hiệu lực
}

export async function loadLive(): Promise<{ rooms: LiveRoom[]; contracts: Contract[] }> {
  const [apiRooms, furnishings, contracts] = await Promise.all([
    api.get<ApiRoom[]>(`/api/chi/rooms?propertyId=${PROPERTY_ID}`),
    api.get<ApiFurnishing[]>('/api/chi/furnishings').catch(() => [] as ApiFurnishing[]),
    liveContracts().catch(() => [] as Contract[]),
  ]);
  const byRoom = new Map(contracts.map((c) => [c.roomId, c]));
  const items = new Map<number, string[]>();
  for (const f of furnishings) {
    const list = items.get(f.roomId) ?? [];
    if (!list.includes(f.itemName)) list.push(f.itemName);
    items.set(f.roomId, list);
  }

  const rooms: LiveRoom[] = apiRooms
    .map((r) => {
      const c = byRoom.get(r.roomId);
      return {
        roomId: r.roomId,
        holdUntil: r.holdUntil,
        roomTypeId: r.roomTypeId,
        roomTypeName: r.roomTypeName,
        initialElectricMeter: r.initialElectricMeter,
        initialWaterMeter: r.initialWaterMeter,
        code: r.roomCode,
        floor: r.floorNumber ?? 0,
        area: Number(r.areaSqm),
        price: Number(r.baseRentPrice),
        deposit: Number(r.depositAmount),
        maxPeople: r.maxOccupants,
        status: roomStatus(r.status, !!c),
        contract: c,
        tenantName: c?.tenantName,
        tenantId: c?.tenantId,
        amenities: items.get(r.roomId) ?? [],
      };
    })
    .sort((a, b) => a.floor - b.floor || a.code.localeCompare(b.code, 'vi', { numeric: true }));

  return { rooms, contracts };
}

/** Gắn hợp đồng đang hiệu lực vào các khách vừa tìm được (khách tải theo trang, không tải cả danh sách). */
export function mapTenants(apiTenants: ApiTenant[], contracts: Contract[]): LiveTenant[] {
  const byTenant = new Map<number, Contract>();
  for (const c of contracts) if (!byTenant.has(c.tenantId)) byTenant.set(c.tenantId, c);
  return apiTenants.map((t) => {
    const c = byTenant.get(t.tenantId);
    return {
      tenantId: t.tenantId,
      name: t.fullName,
      phone: t.phoneNumber,
      idNumber: t.identityNumber,
      email: t.email ?? undefined,
      job: t.occupation ?? '–',
      contract: c,
      roomCode: c?.roomCode,
      since: c?.startDate,
    };
  });
}
