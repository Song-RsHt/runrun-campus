package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserAddress;
import com.zhangxuan.runruncampus.enums.DeliveryZone;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.repository.AddressRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

public record AddressResponse(Long id, String label, String address, DeliveryZone deliveryZone, boolean defaultAddress, Instant createdAt, Instant updatedAt) {
}
