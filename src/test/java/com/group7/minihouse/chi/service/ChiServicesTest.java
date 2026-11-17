package com.group7.minihouse.chi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.chi.dto.BookingRequestDTO;
import com.group7.minihouse.chi.dto.CheckinInspectionDTO;
import com.group7.minihouse.chi.dto.ContractTemplateDTO;
import com.group7.minihouse.chi.dto.RoomDTO;
import com.group7.minihouse.chi.dto.RoomMediaDTO;
import com.group7.minihouse.chi.dto.TenantVehicleDTO;
import com.group7.minihouse.chi.repository.BookingRequestRepository;
import com.group7.minihouse.chi.repository.CheckinInspectionRepository;
import com.group7.minihouse.chi.repository.ContractTemplateRepository;
import com.group7.minihouse.chi.repository.PropertyFloorRepository;
import com.group7.minihouse.chi.repository.RoomEquipmentRequestRepository;
import com.group7.minihouse.chi.repository.RoomMediaRepository;
import com.group7.minihouse.chi.repository.RoomRepository;
import com.group7.minihouse.chi.repository.TenantIdCardRepository;
import com.group7.minihouse.chi.repository.TenantRepository;
import com.group7.minihouse.chi.repository.TenantVehicleRepository;
import com.group7.minihouse.chi.repository.RoomResidentRepository;
import com.group7.minihouse.shared.entity.BookingRequest;
import com.group7.minihouse.shared.entity.ContractTemplate;
import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.RoomEquipmentRequest;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.storage.FileStorage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

/** Kiểm tra các lỗi đã sửa khi rà soát code của Chi (dữ liệu vào sai phải bị từ chối bằng lỗi 4xx, không thành lỗi 500). */
class ChiServicesTest {

    // ------------------------------------------------------------------ helpers

    private static TypedQuery<Long> longQuery(EntityManager em, long result) {
        @SuppressWarnings("unchecked")
        TypedQuery<Long> q = mock(TypedQuery.class);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(result);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(q);
        return q;
    }

    private static Room room(int id) {
        Room r = new Room();
        r.setRoomId(id);
        r.setRoomCode("P" + id);
        r.setMaxOccupants(2);
        return r;
    }

    private static Tenant tenant(int id) {
        Tenant t = new Tenant();
        t.setTenantId(id);
        t.setFullName("Khach " + id);
        return t;
    }

    // ------------------------------------------------------------------ TenantService: xe

    private TenantService tenantService(TenantVehicleRepository vehicles, TenantRepository tenants) {
        return new TenantService(tenants, mock(TenantIdCardRepository.class), vehicles,
                mock(RoomResidentRepository.class), mock(RoomRepository.class));
    }

    private static TenantVehicleDTO.CreateRequest vehicleReq(String plate, String type) {
        TenantVehicleDTO.CreateRequest r = new TenantVehicleDTO.CreateRequest();
        r.setTenantId(1);
        r.setLicensePlate(plate);
        r.setVehicleType(type);
        return r;
    }

    @Test
    void addVehicleRejectsUnknownTypeInsteadOfBreakingDbCheck() {
        TenantService s = tenantService(mock(TenantVehicleRepository.class), mock(TenantRepository.class));
        assertThrows(BusinessException.class, () -> s.addVehicle(vehicleReq("59A1-1", "Tàu thủy")));
    }

    @Test
    void addVehicleRejectsBlankPlate() {
        TenantService s = tenantService(mock(TenantVehicleRepository.class), mock(TenantRepository.class));
        assertThrows(BusinessException.class, () -> s.addVehicle(vehicleReq("  ", "MOTORBIKE")));
    }

    @Test
    void addVehicleDuplicatePlateIsConflict() {
        TenantVehicleRepository vehicles = mock(TenantVehicleRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        when(tenants.findById(1)).thenReturn(Optional.of(tenant(1)));
        when(vehicles.existsByLicensePlateIgnoreCase("59A1-1")).thenReturn(true);
        BusinessException e = assertThrows(BusinessException.class,
                () -> tenantService(vehicles, tenants).addVehicle(vehicleReq("59a1-1", "MOTORBIKE")));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    void vehicleTypeDefaultsAndAcceptsVietnameseNames() {
        assertEquals("MOTORBIKE", TenantService.normalizeVehicleType(null));
        assertEquals("MOTORBIKE", TenantService.normalizeVehicleType("Xe máy"));
        assertEquals("ELECTRIC_BIKE", TenantService.normalizeVehicleType("xe điện"));
        assertEquals("CAR", TenantService.normalizeVehicleType("car"));
    }

    // ------------------------------------------------------------------ RoomService: trạng thái

    private RoomService roomService(RoomRepository rooms, EntityManager em) {
        return new RoomService(rooms, mock(PropertyFloorRepository.class), em);
    }

    @Test
    void roomStatusMustBeKnownValue() {
        RoomService s = roomService(mock(RoomRepository.class), mock(EntityManager.class));
        assertThrows(BusinessException.class, () -> s.updateRoomStatus(1, "BROKEN"));
        assertThrows(BusinessException.class, () -> s.updateRoomStatus(1, null));
    }

    @Test
    void cannotChangeStatusOfRoomWithActiveContract() {
        RoomRepository rooms = mock(RoomRepository.class);
        EntityManager em = mock(EntityManager.class);
        when(rooms.findById(1)).thenReturn(Optional.of(room(1)));
        longQuery(em, 1L);
        assertThrows(BusinessException.class, () -> roomService(rooms, em).updateRoomStatus(1, "VACANT"));
    }

    @Test
    void cannotMarkRentedWithoutContract() {
        RoomRepository rooms = mock(RoomRepository.class);
        EntityManager em = mock(EntityManager.class);
        when(rooms.findById(1)).thenReturn(Optional.of(room(1)));
        longQuery(em, 0L);
        assertThrows(BusinessException.class, () -> roomService(rooms, em).updateRoomStatus(1, "RENTED"));
    }

    @Test
    void roomWithoutContractCanGoToRepairing() {
        RoomRepository rooms = mock(RoomRepository.class);
        EntityManager em = mock(EntityManager.class);
        Room r = room(1);
        when(rooms.findById(1)).thenReturn(Optional.of(r));
        when(rooms.save(any(Room.class))).thenAnswer(i -> i.getArgument(0));
        longQuery(em, 0L);
        RoomDTO dto = roomService(rooms, em).updateRoomStatus(1, "repairing");
        assertEquals("REPAIRING", dto.getStatus());
    }

    @Test
    void createRoomValidatesInputBeforeTouchingDb() {
        RoomService s = roomService(mock(RoomRepository.class), mock(EntityManager.class));
        RoomDTO d = new RoomDTO();
        assertThrows(BusinessException.class, () -> s.createRoom(d)); // thiếu tầng
        d.setFloorId(1);
        assertThrows(BusinessException.class, () -> s.createRoom(d)); // thiếu mã phòng
    }

    // ------------------------------------------------------------------ BookingRequestService: luồng trạng thái

    private BookingRequestService bookingService(BookingRequestRepository repo) {
        when(repo.save(any(BookingRequest.class))).thenAnswer(i -> i.getArgument(0));
        return new BookingRequestService(repo, mock(RoomRepository.class), mock(TenantRepository.class));
    }

    private static BookingRequest booking(String status) {
        BookingRequest b = new BookingRequest();
        b.setBookingId(1);
        b.setStatus(status);
        return b;
    }

    @Test
    void bookingCanOnlyBeApprovedWhilePending() {
        BookingRequestRepository repo = mock(BookingRequestRepository.class);
        when(repo.findById(1)).thenReturn(Optional.of(booking("REJECTED")));
        BusinessException e = assertThrows(BusinessException.class, () -> bookingService(repo).approve(1));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    void pendingBookingCanBeApprovedThenCancelled() {
        BookingRequestRepository repo = mock(BookingRequestRepository.class);
        BookingRequest b = booking("PENDING");
        when(repo.findById(1)).thenReturn(Optional.of(b));
        BookingRequestService s = bookingService(repo);
        assertEquals("APPROVED", s.approve(1).getStatus());
        assertEquals("CANCELLED", s.cancel(1).getStatus());
        assertThrows(BusinessException.class, () -> s.cancel(1)); // đã hủy thì không hủy lại
    }

    @Test
    void bookingRejectsOverCapacityAndNegativeDeposit() {
        BookingRequestRepository repo = mock(BookingRequestRepository.class);
        RoomRepository rooms = mock(RoomRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        when(rooms.findById(1)).thenReturn(Optional.of(room(1))); // tối đa 2 người
        when(tenants.findById(1)).thenReturn(Optional.of(tenant(1)));
        BookingRequestService s = new BookingRequestService(repo, rooms, tenants);

        BookingRequestDTO.CreateRequest req = new BookingRequestDTO.CreateRequest();
        req.setRoomId(1);
        req.setTenantId(1);
        req.setExpectedStartDate(java.time.LocalDate.now().plusDays(3));
        req.setOccupantsCount(5);
        assertThrows(BusinessException.class, () -> s.create(req));
        req.setOccupantsCount(1);
        req.setDepositPaid(new java.math.BigDecimal("-1"));
        assertThrows(BusinessException.class, () -> s.create(req));
    }

    // ------------------------------------------------------------------ RoomEquipmentRequestService

    @Test
    void equipmentStatusMustBeValidAndFlowFromPending() {
        RoomEquipmentRequestRepository repo = mock(RoomEquipmentRequestRepository.class);
        RoomEquipmentRequest e = new RoomEquipmentRequest();
        e.setStatus("REJECTED");
        when(repo.findById(1)).thenReturn(Optional.of(e));
        RoomEquipmentRequestService s = new RoomEquipmentRequestService(repo, mock(RoomRepository.class),
                mock(TenantRepository.class));
        assertThrows(BusinessException.class, () -> s.updateStatus(1, "DONE"));
        assertThrows(BusinessException.class, () -> s.updateStatus(1, null));
        assertThrows(BusinessException.class, () -> s.approve(1)); // đã từ chối thì không duyệt được
    }

    // ------------------------------------------------------------------ CheckinInspectionService

    private static CheckinInspectionDTO.CreateRequest checkinReq(Integer contractId) {
        CheckinInspectionDTO.CreateRequest r = new CheckinInspectionDTO.CreateRequest();
        r.setRoomId(1);
        r.setTenantId(1);
        r.setContractId(contractId);
        r.setInitialElectricMeter(120);
        r.setInitialWaterMeter(15);
        return r;
    }

    private static RentalContract contract(int id, Room r, Tenant t) {
        RentalContract c = new RentalContract();
        c.setContractId(id);
        c.setContractCode("HD" + id);
        c.setRoom(r);
        c.setTenant(t);
        return c;
    }

    @Test
    void checkinRejectsContractOfAnotherRoom() {
        CheckinInspectionRepository repo = mock(CheckinInspectionRepository.class);
        RoomRepository rooms = mock(RoomRepository.class);
        EntityManager em = mock(EntityManager.class);
        when(rooms.findById(1)).thenReturn(Optional.of(room(1)));
        when(em.find(Tenant.class, 1)).thenReturn(tenant(1));
        when(em.find(RentalContract.class, 9)).thenReturn(contract(9, room(2), tenant(1)));
        CheckinInspectionService s = new CheckinInspectionService(repo, rooms, em);
        assertThrows(BusinessException.class, () -> s.create(checkinReq(9)));
        verify(repo, never()).save(any());
    }

    @Test
    void checkinRejectsSecondReportForSameContract() {
        CheckinInspectionRepository repo = mock(CheckinInspectionRepository.class);
        RoomRepository rooms = mock(RoomRepository.class);
        EntityManager em = mock(EntityManager.class);
        Room r = room(1);
        Tenant t = tenant(1);
        when(rooms.findById(1)).thenReturn(Optional.of(r));
        when(em.find(Tenant.class, 1)).thenReturn(t);
        when(em.find(RentalContract.class, 9)).thenReturn(contract(9, r, t));
        when(repo.existsByContract_ContractId(9)).thenReturn(true);
        CheckinInspectionService s = new CheckinInspectionService(repo, rooms, em);
        BusinessException e = assertThrows(BusinessException.class, () -> s.create(checkinReq(9)));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    void checkinDoesNotRewriteMeterBaselineOfRoomThatAlreadyHasReadings() {
        CheckinInspectionRepository repo = mock(CheckinInspectionRepository.class);
        RoomRepository rooms = mock(RoomRepository.class);
        EntityManager em = mock(EntityManager.class);
        Room r = room(1);
        r.setInitialElectricMeter(100);
        r.setInitialWaterMeter(10);
        when(rooms.findById(1)).thenReturn(Optional.of(r));
        when(em.find(Tenant.class, 1)).thenReturn(tenant(1));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        longQuery(em, 3L); // phòng đã có 3 lần ghi chỉ số
        new CheckinInspectionService(repo, rooms, em).create(checkinReq(null));
        assertEquals(100, r.getInitialElectricMeter());
        assertEquals(10, r.getInitialWaterMeter());
        verify(rooms, never()).save(any(Room.class));
    }

    @Test
    void checkinSetsMeterBaselineForRoomWithoutReadings() {
        CheckinInspectionRepository repo = mock(CheckinInspectionRepository.class);
        RoomRepository rooms = mock(RoomRepository.class);
        EntityManager em = mock(EntityManager.class);
        Room r = room(1);
        r.setInitialElectricMeter(0);
        r.setInitialWaterMeter(0);
        when(rooms.findById(1)).thenReturn(Optional.of(r));
        when(em.find(Tenant.class, 1)).thenReturn(tenant(1));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        longQuery(em, 0L);
        new CheckinInspectionService(repo, rooms, em).create(checkinReq(null));
        assertEquals(120, r.getInitialElectricMeter());
        assertEquals(15, r.getInitialWaterMeter());
    }

    // ------------------------------------------------------------------ RoomMediaService: upload ảnh

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Test
    void uploadRejectsNonImageFileEvenWithImageName(@TempDir Path dir) {
        RoomRepository rooms = mock(RoomRepository.class);
        when(rooms.findById(1)).thenReturn(Optional.of(room(1)));
        RoomMediaService s = new RoomMediaService(mock(RoomMediaRepository.class), rooms,
                new FileStorage(dir.toString()));
        MockMultipartFile fake = new MockMultipartFile("file", "anh.png", "image/png", "<script>alert(1)</script>".getBytes());
        assertThrows(BusinessException.class, () -> s.uploadMedia(1, fake, false));
        assertFalse(dir.resolve("rooms").toFile().exists() && dir.resolve("rooms").toFile().list().length > 0);
    }

    @Test
    void uploadStoresRealImageUnderRandomName(@TempDir Path dir) throws Exception {
        RoomRepository rooms = mock(RoomRepository.class);
        RoomMediaRepository media = mock(RoomMediaRepository.class);
        when(rooms.findById(1)).thenReturn(Optional.of(room(1)));
        when(media.save(any())).thenAnswer(i -> i.getArgument(0));
        RoomMediaService s = new RoomMediaService(media, rooms, new FileStorage(dir.toString()));
        RoomMediaDTO dto = s.uploadMedia(1, new MockMultipartFile("file", "../../evil.jsp", "image/png", PNG), true);
        assertTrue(dto.getMediaUrl().startsWith("/uploads/rooms/"));
        assertTrue(dto.getMediaUrl().endsWith(".png"));
        assertFalse(dto.getMediaUrl().contains("evil"));
        assertEquals(1, dir.resolve("rooms").toFile().list().length);
    }

    @Test
    void addMediaOnlyAcceptsHttpOrOwnUploadPath() {
        RoomRepository rooms = mock(RoomRepository.class);
        when(rooms.findById(1)).thenReturn(Optional.of(room(1)));
        RoomMediaService s = new RoomMediaService(mock(RoomMediaRepository.class), rooms, mock(FileStorage.class));
        RoomMediaDTO.CreateRequest req = new RoomMediaDTO.CreateRequest();
        req.setRoomId(1);
        req.setMediaUrl("javascript:alert(1)");
        assertThrows(BusinessException.class, () -> s.addMedia(req));
        req.setMediaUrl("");
        assertThrows(BusinessException.class, () -> s.addMedia(req));
    }

    // ------------------------------------------------------------------ PropertyService + lọc phòng theo khu

    @Test
    void propertyListHasRoomCountAndFloors() {
        com.group7.minihouse.chi.repository.PropertyRepository props =
                mock(com.group7.minihouse.chi.repository.PropertyRepository.class);
        RoomRepository rooms = mock(RoomRepository.class);
        PropertyFloorRepository floors = mock(PropertyFloorRepository.class);
        com.group7.minihouse.shared.entity.Property p = new com.group7.minihouse.shared.entity.Property();
        p.setPropertyId(7);
        p.setPropertyName("Khu A");
        com.group7.minihouse.shared.entity.PropertyFloor f = new com.group7.minihouse.shared.entity.PropertyFloor();
        f.setFloorId(70);
        f.setFloorNumber(1);
        f.setFloorName("Tầng 1");
        when(props.findAllByOrderByPropertyIdAsc()).thenReturn(List.of(p));
        when(rooms.countByProperty_PropertyId(7)).thenReturn(12L);
        when(floors.findByProperty_PropertyIdOrderByFloorNumberAsc(7)).thenReturn(List.of(f));

        var list = new PropertyService(props, rooms, floors).getAll();

        assertEquals(1, list.size());
        assertEquals(12L, list.get(0).getRoomCount());
        assertEquals(1, list.get(0).getFloors().size());
        assertEquals("Tầng 1", list.get(0).getFloors().get(0).getFloorName());
    }

    @Test
    void roomListCanBeFilteredByProperty() {
        RoomRepository rooms = mock(RoomRepository.class);
        when(rooms.findByProperty_PropertyId(7)).thenReturn(List.of(room(1)));
        List<RoomDTO> list = roomService(rooms, mock(EntityManager.class)).getAllRooms(7);
        assertEquals(1, list.size());
        verify(rooms, never()).findAll();
    }

    // ------------------------------------------------------------------ ContractTemplateService

    @Test
    void newDefaultTemplateClearsPreviousDefault() {
        ContractTemplateRepository repo = mock(ContractTemplateRepository.class);
        ContractTemplate old = new ContractTemplate();
        old.setTemplateId(1);
        old.setTemplateName("Cu");
        old.setTermsContent("x");
        old.setIsDefault(true);
        when(repo.findAll()).thenReturn(List.of(old));
        when(repo.save(any(ContractTemplate.class))).thenAnswer(i -> i.getArgument(0));
        ContractTemplateDTO.Request req = new ContractTemplateDTO.Request();
        req.setTemplateName("Moi");
        req.setTermsContent("dieu khoan");
        req.setIsDefault(true);
        new ContractTemplateService(repo).create(req);
        assertFalse(old.getIsDefault());
    }
}
