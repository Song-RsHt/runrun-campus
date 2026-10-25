package com.zhangxuan.runruncampus.order;

import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderProofService {
    private static final long MAX_FILE_SIZE = 8L * 1024 * 1024;
    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    );

    private final OrderRepository orderRepository;
    private final OrderProofRepository proofRepository;
    private final OrderProofStorage storage;

    public OrderProofService(OrderRepository orderRepository, OrderProofRepository proofRepository,
                             OrderProofStorage storage) {
        this.orderRepository = orderRepository;
        this.proofRepository = proofRepository;
        this.storage = storage;
    }

    @Transactional
    public ProofResponse upload(AppUser courier, Long orderId, OrderProofType type, MultipartFile file) {
        if (courier.getRole() != UserRole.COURIER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有骑手可以上传配送凭证");
        }

        CampusOrder order = orderRepository.findLockedById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (order.getCourier() == null || !courier.getId().equals(order.getCourier().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该订单不属于当前骑手");
        }

        OrderStatus requiredStatus = type == OrderProofType.DELIVERY ? OrderStatus.PICKED_UP : OrderStatus.ACCEPTED;
        if (order.getStatus() != requiredStatus) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单当前状态不能上传此凭证");
        }
        if (type == OrderProofType.SIZE_REVIEW &&
                (!order.getServiceType().isPackageDelivery() || order.isPackageSizeConfirmed())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前订单不需要规格核验照片");
        }
        if (type == OrderProofType.PICKUP && order.getServiceType().isPackageDelivery()
                && !order.isPackageSizeConfirmed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先完成包裹规格确认");
        }
        if (proofRepository.existsByOrderAndType(order, type)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该凭证已上传");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择凭证图片");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "凭证图片不能超过 8MB");
        }

        String originalFileName = safeOriginalFileName(file.getOriginalFilename());
        String expectedContentType = CONTENT_TYPE_BY_EXTENSION.get(extensionOf(originalFileName));
        if (expectedContentType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 JPG、PNG 或 WEBP 图片");
        }
        if (!expectedContentType.equals(contentType) || !contentType.equals(detectContentType(file))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片类型与文件内容不一致");
        }

        String storedFileName = orderId + "-" + type.name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID()
                + extensionFor(contentType);
        try {
            try (var input = file.getInputStream()) {
                storage.store(storedFileName, contentType, file.getSize(), input);
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "凭证保存失败", exception);
        }

        try {
            OrderProof proof = proofRepository.save(new OrderProof(order, courier, type, storedFileName,
                    originalFileName, contentType, file.getSize()));
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "凭证图片文件名不正确");
        }
        String normalized = value.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (fileName.isBlank() || fileName.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "凭证图片文件名不正确");
        }
        return fileName;
    }

    private String extensionOf(String fileName) {
        int separator = fileName.lastIndexOf('.');
        if (separator < 0 || separator == fileName.length() - 1) return "";
        return fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private String detectContentType(MultipartFile file) {
        byte[] signature;
        try (var input = file.getInputStream()) {
            signature = input.readNBytes(12);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取凭证图片", exception);
        }

        if (startsWith(signature, 0xff, 0xd8, 0xff)) return "image/jpeg";
        if (startsWith(signature, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) return "image/png";
        if (startsWith(signature, 0x52, 0x49, 0x46, 0x46)
                && startsWithAt(signature, 8, 0x57, 0x45, 0x42, 0x50)) return "image/webp";
        return "";
    }

    private boolean startsWith(byte[] value, int... expected) {
        return startsWithAt(value, 0, expected);
    }

    private boolean startsWithAt(byte[] value, int offset, int... expected) {
        if (value.length < offset + expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if ((value[offset + index] & 0xff) != expected[index]) return false;
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

    private ProofResponse response(OrderProof proof) {
        return new ProofResponse(proof.getId(), proof.getType(), proof.getOriginalFileName(), proof.getContentType(),
                proof.getSizeBytes(), proof.getCreatedAt());
    }

    public record ProofResponse(Long id, OrderProofType type, String originalFileName, String contentType,
                                long sizeBytes, Instant createdAt) { }
}
