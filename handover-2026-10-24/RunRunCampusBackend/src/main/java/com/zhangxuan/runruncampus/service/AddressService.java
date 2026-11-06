package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserAddress;
import com.zhangxuan.runruncampus.enums.DeliveryZone;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.repository.AddressRepository;
import com.zhangxuan.runruncampus.vo.AddressResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AddressService {

    List<AddressResponse> list(AppUser user);

    AddressResponse create(AppUser user, String label, String address, boolean defaultAddress);

    AddressResponse create(AppUser user, String label, String address, DeliveryZone deliveryZone, boolean defaultAddress);

    AddressResponse update(AppUser user, Long id, String label, String address, boolean defaultAddress);

    AddressResponse update(AppUser user, Long id, String label, String address, DeliveryZone deliveryZone, boolean defaultAddress);

    AddressResponse setDefault(AppUser user, Long id);

    void delete(AppUser user, Long id);
}
