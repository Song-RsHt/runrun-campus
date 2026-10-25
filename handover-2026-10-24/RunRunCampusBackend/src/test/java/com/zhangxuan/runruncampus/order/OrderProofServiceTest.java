package com.zhangxuan.runruncampus.order;

import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderProofServiceTest {
    private static final byte[] JPEG_BYTES = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00
    };

    @TempDir
    Path storageDirectory;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderProofRepository proofRepository;

    private OrderProofService proofService;
    private AppUser courier;
    private CampusOrder order;

    @BeforeEach
    void setUp() {
        AppUser customer = user(1L, "13800000001", UserRole.CUSTOMER);
        courier = user(2L, "13800000002", UserRole.COURIER);
        order = new CampusOrder("RP-TEST-002", customer, "测试商家", "5678", "东门", "4号楼", ServiceType.LOBBY);
        ReflectionTestUtils.setField(order, "id", 11L);
        order.assignCourier(courier);
        proofService = new OrderProofService(orderRepository, proofRepository,
                new LocalOrderProofStorage(storageDirectory.toString()));
    }

    @Test
    void courierCanUploadPickupProofForAcceptedOrder() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "file", "pickup.jpg", "image/jpeg", JPEG_BYTES);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(proofRepository.existsByOrderAndType(order, OrderProofType.PICKUP)).thenReturn(false);
        when(proofRepository.save(any(OrderProof.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderProofService.ProofResponse response = proofService.upload(courier, 11L, OrderProofType.PICKUP, image);

        assertEquals(OrderProofType.PICKUP, response.type());
        assertEquals("pickup.jpg", response.originalFileName());
        try (var files = Files.list(storageDirectory)) {
            assertTrue(files.findAny().isPresent());
        }
    }

    @Test
    void courierCanUploadPngProofWhenHeaderExtensionAndSignatureMatch() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "file", "pickup.png", "image/png", PNG_BYTES);
        allowPickupProofUpload();
        when(proofRepository.save(any(OrderProof.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderProofService.ProofResponse response = proofService.upload(
                courier, 11L, OrderProofType.PICKUP, image);

        assertEquals("image/png", response.contentType());
        assertEquals("pickup.png", response.originalFileName());
        try (var files = Files.list(storageDirectory)) {
            assertTrue(files.allMatch(file -> file.getFileName().toString().endsWith(".png")));
        }
    }

    @Test
    void rejectsSpoofedJpegContent() {
        MockMultipartFile image = new MockMultipartFile(
                "file", "pickup.jpg", "image/jpeg", "<script>alert(1)</script>".getBytes());
        allowPickupProofUpload();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> proofService.upload(courier, 11L, OrderProofType.PICKUP, image));

        assertEquals(400, exception.getStatusCode().value());
        verify(proofRepository, never()).save(any(OrderProof.class));
    }

    @Test
    void rejectsContentTypeThatDoesNotMatchImageSignature() {
        MockMultipartFile image = new MockMultipartFile(
                "file", "pickup.jpg", "image/jpeg", PNG_BYTES);
        allowPickupProofUpload();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> proofService.upload(courier, 11L, OrderProofType.PICKUP, image));

        assertEquals(400, exception.getStatusCode().value());
        verify(proofRepository, never()).save(any(OrderProof.class));
    }

    @Test
    void rejectsExecutableExtensionEvenWhenMimeAndSignatureLookValid() {
        MockMultipartFile image = new MockMultipartFile(
                "file", "pickup.jpg.php", "image/jpeg", JPEG_BYTES);
        allowPickupProofUpload();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> proofService.upload(courier, 11L, OrderProofType.PICKUP, image));

        assertEquals(400, exception.getStatusCode().value());
        verify(proofRepository, never()).save(any(OrderProof.class));
    }

    @Test
    void rejectsFileLargerThanEightMegabytesBeforeReadingIt() {
        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(image.getSize()).thenReturn(8L * 1024 * 1024 + 1);
        allowPickupProofUpload();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> proofService.upload(courier, 11L, OrderProofType.PICKUP, image));

        assertEquals(400, exception.getStatusCode().value());
        verify(proofRepository, never()).save(any(OrderProof.class));
    }

    @Test
    void packagePickupProofRequiresConfirmedSize() {
        CampusOrder packageOrder = new CampusOrder(
                "RP-PACKAGE-002", order.getCustomer(), "菜鸟驿站", "5678", "校内快递驿站", "4号楼",
                ServiceType.PACKAGE_SMALL);
        ReflectionTestUtils.setField(packageOrder, "id", 12L);
        packageOrder.assignCourier(courier);
        MockMultipartFile image = new MockMultipartFile(
                "file", "pickup.jpg", "image/jpeg", JPEG_BYTES);
        when(orderRepository.findLockedById(12L)).thenReturn(Optional.of(packageOrder));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> proofService.upload(courier, 12L, OrderProofType.PICKUP, image));

        assertEquals(409, exception.getStatusCode().value());
        verify(proofRepository, never()).save(any(OrderProof.class));
    }

    @Test
    void courierCanUploadSizeReviewProofBeforePackageSizeIsConfirmed() throws Exception {
        CampusOrder packageOrder = new CampusOrder(
                "RP-PACKAGE-003", order.getCustomer(), "菜鸟驿站", "5678", "校内快递驿站", "4号楼",
                ServiceType.PACKAGE_SMALL);
        ReflectionTestUtils.setField(packageOrder, "id", 12L);
        packageOrder.assignCourier(courier);
        MockMultipartFile image = new MockMultipartFile(
                "file", "size-review.jpg", "image/jpeg", JPEG_BYTES);
        when(orderRepository.findLockedById(12L)).thenReturn(Optional.of(packageOrder));
        when(proofRepository.existsByOrderAndType(packageOrder, OrderProofType.SIZE_REVIEW)).thenReturn(false);
        when(proofRepository.save(any(OrderProof.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderProofService.ProofResponse response = proofService.upload(
                courier, 12L, OrderProofType.SIZE_REVIEW, image);

        assertEquals(OrderProofType.SIZE_REVIEW, response.type());
        assertEquals("size-review.jpg", response.originalFileName());
        try (var files = Files.list(storageDirectory)) {
            assertTrue(files.findAny().isPresent());
        }
    }

    @Test
    void sizeReviewProofIsOnlyAvailableForUnconfirmedPackageOrders() {
        MockMultipartFile image = new MockMultipartFile(
                "file", "size-review.jpg", "image/jpeg", JPEG_BYTES);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> proofService.upload(courier, 11L, OrderProofType.SIZE_REVIEW, image));

        assertEquals(409, exception.getStatusCode().value());
        verify(proofRepository, never()).save(any(OrderProof.class));
    }

    @Test
    void storageFailureDoesNotCreateProofRecord() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "file", "pickup.jpg", "image/jpeg", JPEG_BYTES);
        OrderProofStorage failingStorage = mock(OrderProofStorage.class);
        OrderProofService service = new OrderProofService(orderRepository, proofRepository, failingStorage);
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(proofRepository.existsByOrderAndType(order, OrderProofType.PICKUP)).thenReturn(false);
        doThrow(new IOException("storage unavailable")).when(failingStorage)
                .store(anyString(), anyString(), anyLong(), any(InputStream.class));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.upload(courier, 11L, OrderProofType.PICKUP, image));

        assertEquals(500, exception.getStatusCode().value());
        verify(proofRepository, never()).save(any(OrderProof.class));
    }

    private void allowPickupProofUpload() {
        when(orderRepository.findLockedById(11L)).thenReturn(Optional.of(order));
        when(proofRepository.existsByOrderAndType(order, OrderProofType.PICKUP)).thenReturn(false);
    }

    private AppUser user(Long id, String phone, UserRole role) {
        AppUser user = new AppUser(phone, "password-hash", "测试账号", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
