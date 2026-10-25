package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderEventRepository;
import com.zhangxuan.runruncampus.order.OrderIssueRepository;
import com.zhangxuan.runruncampus.order.OrderProofRepository;
import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.order.ServiceType;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderEventRepository eventRepository;
    @Mock
    private OrderIssueRepository issueRepository;
    @Mock
    private OrderProofRepository proofRepository;

    private AdminOrderService service;

    @BeforeEach
    void setUp() {
        service = new AdminOrderService(orderRepository, eventRepository, issueRepository, proofRepository);
    }

    @Test
    void listsOrdersWithTrimmedQueryAndStablePagination() {
        CampusOrder order = order();
        PageRequest request = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(orderRepository.searchForAdmin(eq("RP-001"), eq(OrderStatus.PENDING_ASSIGNMENT), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), request, 11));

        AdminOrderController.AdminOrderPageResponse response = service.list(
                "  RP-001  ", OrderStatus.PENDING_ASSIGNMENT, false, 1, 10);

        assertEquals(1, response.items().size());
        assertEquals("RP-001", response.items().get(0).orderNo());
        assertEquals("测试用户", response.items().get(0).customer().displayName());
        assertNull(response.items().get(0).courier());
        assertEquals(11, response.totalElements());
        assertEquals(2, response.totalPages());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).searchForAdmin(eq("RP-001"), eq(OrderStatus.PENDING_ASSIGNMENT), eq(false), pageable.capture());
        assertEquals(Sort.Direction.DESC, pageable.getValue().getSort().getOrderFor("createdAt").getDirection());
    }

    @Test
    void archivedViewUsesArchivedFilter() {
        when(orderRepository.filterForAdmin(eq(null), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(null, null, true, 0, 20);

        verify(orderRepository).filterForAdmin(eq(null), eq(true), any(Pageable.class));
    }

    @Test
    void detailIncludesOrderEvidenceCollections() {
        CampusOrder order = order();
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(eventRepository.findByOrderOrderByCreatedAtAsc(order)).thenReturn(List.of());
        when(issueRepository.findByOrderOrderByCreatedAtAsc(order)).thenReturn(List.of());
        when(proofRepository.findByOrderOrderByCreatedAtAsc(order)).thenReturn(List.of());

        AdminOrderController.AdminOrderDetailResponse response = service.detail(21L);

        assertEquals("5678", response.pickupCode());
        assertEquals("东门", response.pickupLocation());
        assertEquals(200, response.originalFeeCents());
        assertEquals(List.of(), response.events());
        assertEquals(List.of(), response.issues());
        assertEquals(List.of(), response.proofs());
    }

    @Test
    void missingOrderReturnsNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.detail(99L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void exportsExcelCompatibleCsvAndNeutralizesFormulaCells() {
        CampusOrder order = order();
        ReflectionTestUtils.setField(order, "merchant", "=SUM(1,1)");
        PageRequest request = PageRequest.of(0, AdminOrderService.EXPORT_LIMIT,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        when(orderRepository.filterForAdmin(eq(null), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), request, 1));

        AdminOrderService.OrderExport export = service.exportCsv(null, null, false);
        byte[] content = export.content();
        String csv = new String(content, 3, content.length - 3, StandardCharsets.UTF_8);

        assertEquals((byte) 0xEF, content[0]);
        assertEquals((byte) 0xBB, content[1]);
        assertEquals((byte) 0xBF, content[2]);
        assertEquals(false, export.truncated());
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"'=SUM(1,1)\""));
        org.junit.jupiter.api.Assertions.assertTrue(csv.startsWith("订单号,状态,服务类型"));
    }

    private CampusOrder order() {
        AppUser customer = new AppUser("13800000001", "hash", "测试用户", UserRole.CUSTOMER);
        ReflectionTestUtils.setField(customer, "id", 7L);
        CampusOrder order = new CampusOrder(
                "RP-001", customer, "阿城小面", "5678", "东门", "3号楼", ServiceType.LOBBY);
        ReflectionTestUtils.setField(order, "id", 21L);
        return order;
    }
}
