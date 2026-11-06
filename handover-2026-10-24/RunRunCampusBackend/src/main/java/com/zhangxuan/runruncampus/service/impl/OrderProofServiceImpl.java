package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderProof;
import com.zhangxuan.runruncampus.enums.OrderProofType;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.integration.storage.OrderProofStorage;
import com.zhangxuan.runruncampus.model.UploadSource;
import com.zhangxuan.runruncampus.repository.OrderProofRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.service.OrderProofService;
import com.zhangxuan.runruncampus.vo.OrderProofVO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 订单私有图片凭证业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。 */
@Service
public class OrderProofServiceImpl implements OrderProofService {

    private static final long MAX_FILE_SIZE = 8L * 1024 * 1024;
    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION =
            Map.of(
                    "jpg",
                    "image/jpeg",
                    "jpeg",
                    "image/jpeg",
                    "png",
                    "image/png",
                    "webp",
                    "image/webp");

    private final OrderRepository orderRepository;
    private final OrderProofRepository proofRepository;
    private final OrderProofStorage storage;

    public OrderProofServiceImpl(
            OrderRepository orderRepository,
            OrderProofRepository proofRepository,
            OrderProofStorage storage) {
        this.orderRepository = orderRepository;
        this.proofRepository = proofRepository;
        this.storage = storage;
    }

    @Transactional
    @Override
    public OrderProofVO upload(
            AppUser courier, Long orderId, OrderProofType type, UploadSource file) {
        if (courier.getRole() != UserRole.COURIER) {
            throw new BusinessFailure(403, "只有骑手可以上传配送凭证");
        }
        CampusOrder order =
                orderRepository
                        .findLockedById(orderId)
                        .orElseThrow(() -> new BusinessFailure(404, "订单不存在"));
        if (order.getCourier() == null || !courier.getId().equals(order.getCourier().getId())) {
            throw new BusinessFailure(403, "该订单不属于当前骑手");
        }
        OrderStatus requiredStatus =
                type == OrderProofType.DELIVERY ? OrderStatus.PICKED_UP : OrderStatus.ACCEPTED;
        if (order.getStatus() != requiredStatus) {
            throw new BusinessFailure(409, "订单当前状态不能上传此凭证");
        }
        if (type == OrderProofType.SIZE_REVIEW
                && (!order.getServiceType().isPackageDelivery()
                        || order.isPackageSizeConfirmed())) {
            throw new BusinessFailure(409, "当前订单不需要规格核验照片");
        }
        if (type == OrderProofType.PICKUP
                && order.getServiceType().isPackageDelivery()
                && !order.isPackageSizeConfirmed()) {
            throw new BusinessFailure(409, "请先完成包裹规格确认");
        }
        if (proofRepository.existsByOrderAndType(order, type)) {
            throw new BusinessFailure(409, "该凭证已上传");
        }
        String contentType =
                file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (file.isEmpty()) {
            throw new BusinessFailure(400, "请选择凭证图片");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessFailure(400, "凭证图片不能超过 8MB");
        }
        String originalFileName = safeOriginalFileName(file.getOriginalFilename());
        String expectedContentType = CONTENT_TYPE_BY_EXTENSION.get(extensionOf(originalFileName));
        if (expectedContentType == null) {
            throw new BusinessFailure(400, "仅支持 JPG、PNG 或 WEBP 图片");
        }
        if (!expectedContentType.equals(contentType)
                || !contentType.equals(detectContentType(file))) {
            throw new BusinessFailure(400, "图片类型与文件内容不一致");
        }
        String storedFileName =
                orderId
                        + "-"
                        + type.name().toLowerCase(Locale.ROOT)
                        + "-"
                        + UUID.randomUUID()
                        + extensionFor(contentType);
        try {
            try (var input = file.getInputStream()) {
                storage.store(storedFileName, contentType, file.getSize(), input);
            }
        } catch (IOException exception) {
            throw new BusinessFailure(500, "凭证保存失败", exception);
        }
        try {
            OrderProof proof =
                    proofRepository.save(
                            new OrderProof(
                                    order,
                                    courier,
                                    type,
                                    storedFileName,
                                    originalFileName,
                                    contentType,
                                    file.getSize()));
            return response(proof);
        } catch (RuntimeException exception) {
            try {
                storage.delete(storedFileName);
            } catch (IOException ignored) {
            }
            throw exception;
        }
    }

    private String safeOriginalFileName(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessFailure(400, "凭证图片文件名不正确");
        }
        String normalized = value.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (fileName.isBlank() || fileName.length() > 255) {
            throw new BusinessFailure(400, "凭证图片文件名不正确");
        }
        return fileName;
    }

    private String extensionOf(String fileName) {
        int separator = fileName.lastIndexOf('.');
        if (separator < 0 || separator == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private String detectContentType(UploadSource file) {
        byte[] signature;
        try (var input = file.getInputStream()) {
            signature = input.readNBytes(12);
        } catch (IOException exception) {
            throw new BusinessFailure(400, "无法读取凭证图片", exception);
        }
        if (startsWith(signature, 0xff, 0xd8, 0xff)) {
            return "image/jpeg";
        }
        if (startsWith(signature, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) {
            return "image/png";
        }
        if (startsWith(signature, 0x52, 0x49, 0x46, 0x46)
                && startsWithAt(signature, 8, 0x57, 0x45, 0x42, 0x50)) {
            return "image/webp";
        }
        return "";
    }

    private boolean startsWith(byte[] value, int... expected) {
        return startsWithAt(value, 0, expected);
    }

    private boolean startsWithAt(byte[] value, int offset, int... expected) {
        if (value.length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if ((value[offset + index] & 0xff) != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private OrderProofVO response(OrderProof proof) {
        return new OrderProofVO(
                proof.getId(),
                proof.getType(),
                proof.getOriginalFileName(),
                proof.getContentType(),
                proof.getSizeBytes(),
                proof.getCreatedAt());
    }
}
