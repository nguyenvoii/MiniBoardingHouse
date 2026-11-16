// DỮ LIỆU MẪU cho phần của Chi (phòng, khách thuê). Khi Chi có API thật, thay các hàm trong file này bằng lời gọi API,
// các màn hình không phải vẽ lại. Mọi nơi dùng dữ liệu này đều hiện nhãn "Dữ liệu mẫu".
export interface SampleRoom {
  roomId: number; code: string; floor: number; area: number; price: number; deposit: number; maxPeople: number;
  status: 'RENTED' | 'VACANT' | 'REPAIRING'; tenantId?: number; amenities: string[];
}
export interface SampleTenant {
  tenantId: number; name: string; phone: string; idNumber: string; email?: string; job: string; roomCode?: string; since?: string; vehicles: string[];
}

export const SAMPLE_ROOMS: SampleRoom[] = [
  { roomId: 1, code: 'P101', floor: 1, area: 22, price: 3000000, deposit: 3000000, maxPeople: 2, status: 'RENTED', tenantId: 1, amenities: ['Máy lạnh', 'Ban công'] },
  { roomId: 2, code: 'P102', floor: 1, area: 20, price: 2800000, deposit: 2800000, maxPeople: 2, status: 'RENTED', tenantId: 2, amenities: ['Máy lạnh'] },
  { roomId: 3, code: 'P103', floor: 1, area: 20, price: 2800000, deposit: 2800000, maxPeople: 2, status: 'VACANT', amenities: ['Máy lạnh'] },
  { roomId: 4, code: 'P104', floor: 1, area: 25, price: 3300000, deposit: 3300000, maxPeople: 3, status: 'RENTED', tenantId: 3, amenities: ['Máy lạnh', 'Ban công', 'Bếp riêng'] },
  { roomId: 5, code: 'P201', floor: 2, area: 22, price: 3100000, deposit: 3100000, maxPeople: 2, status: 'RENTED', tenantId: 4, amenities: ['Máy lạnh', 'Ban công'] },
  { roomId: 6, code: 'P202', floor: 2, area: 20, price: 2900000, deposit: 2900000, maxPeople: 2, status: 'REPAIRING', amenities: ['Máy lạnh'] },
  { roomId: 7, code: 'P203', floor: 2, area: 20, price: 2900000, deposit: 2900000, maxPeople: 2, status: 'RENTED', tenantId: 5, amenities: ['Máy lạnh'] },
  { roomId: 8, code: 'P204', floor: 2, area: 25, price: 3400000, deposit: 3400000, maxPeople: 3, status: 'VACANT', amenities: ['Máy lạnh', 'Ban công', 'Bếp riêng'] },
  { roomId: 9, code: 'P301', floor: 3, area: 22, price: 3200000, deposit: 3200000, maxPeople: 2, status: 'RENTED', tenantId: 6, amenities: ['Máy lạnh', 'Ban công'] },
  { roomId: 10, code: 'P302', floor: 3, area: 20, price: 3000000, deposit: 3000000, maxPeople: 2, status: 'RENTED', tenantId: 7, amenities: ['Máy lạnh'] },
  { roomId: 11, code: 'P303', floor: 3, area: 20, price: 3000000, deposit: 3000000, maxPeople: 2, status: 'VACANT', amenities: ['Máy lạnh'] },
  { roomId: 12, code: 'P304', floor: 3, area: 25, price: 3500000, deposit: 3500000, maxPeople: 3, status: 'RENTED', tenantId: 8, amenities: ['Máy lạnh', 'Ban công', 'Bếp riêng'] },
];

export const SAMPLE_TENANTS: SampleTenant[] = [
  { tenantId: 1, name: 'Nguyễn Văn An', phone: '0901 234 567', idNumber: '079201001234', email: 'an.nguyen@example.com', job: 'Kỹ sư phần mềm', roomCode: 'P101', since: '2026-03-01', vehicles: ['59A1-123.45'] },
  { tenantId: 2, name: 'Trần Thị Bích', phone: '0912 345 678', idNumber: '079301002345', job: 'Kế toán', roomCode: 'P102', since: '2026-05-15', vehicles: ['59C2-678.90'] },
  { tenantId: 3, name: 'Lê Minh Châu', phone: '0933 456 789', idNumber: '079202003456', email: 'chau.le@example.com', job: 'Giáo viên', roomCode: 'P104', since: '2025-11-01', vehicles: [] },
  { tenantId: 4, name: 'Phạm Quốc Dũng', phone: '0944 567 890', idNumber: '079203004567', job: 'Nhân viên ngân hàng', roomCode: 'P201', since: '2026-01-10', vehicles: ['59X3-111.22'] },
  { tenantId: 5, name: 'Võ Thị Hà', phone: '0955 678 901', idNumber: '079304005678', job: 'Dược sĩ', roomCode: 'P203', since: '2026-07-01', vehicles: ['59F1-222.33'] },
  { tenantId: 6, name: 'Đặng Hoàng Khang', phone: '0966 789 012', idNumber: '079205006789', job: 'Sinh viên', roomCode: 'P301', since: '2026-08-20', vehicles: [] },
  { tenantId: 7, name: 'Bùi Ngọc Lan', phone: '0977 890 123', idNumber: '079306007890', job: 'Thiết kế đồ họa', roomCode: 'P302', since: '2026-02-01', vehicles: ['59G4-333.44'] },
  { tenantId: 8, name: 'Hồ Thanh Mai', phone: '0988 901 234', idNumber: '079307008901', email: 'mai.ho@example.com', job: 'Bác sĩ', roomCode: 'P304', since: '2025-12-12', vehicles: ['51H-555.66'] },
];

export const SAMPLE_BANK = { bankName: 'Vietcombank', accountNumber: '0071001234567', holder: 'CHU TRO STAYLY' };
