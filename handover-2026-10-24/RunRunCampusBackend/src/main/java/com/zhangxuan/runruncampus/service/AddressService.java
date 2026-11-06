package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.DeliveryZone;
import com.zhangxuan.runruncampus.vo.AddressResponse;
import java.util.List;















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
