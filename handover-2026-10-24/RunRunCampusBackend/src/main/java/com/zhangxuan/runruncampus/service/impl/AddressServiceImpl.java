package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserAddress;
import com.zhangxuan.runruncampus.enums.DeliveryZone;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.repository.AddressRepository;
import com.zhangxuan.runruncampus.service.AddressService;
import com.zhangxuan.runruncampus.vo.AddressResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 收件地址业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。 */
@Service
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;

    public AddressServiceImpl(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public List<AddressResponse> list(AppUser user) {
        requireCustomer(user);
        return addresses(user).stream().map(this::response).toList();
    }

    @Transactional
    @Override
    public AddressResponse create(
            AppUser user, String label, String address, boolean defaultAddress) {
        return create(user, label, address, DeliveryZone.fromLegacy(address), defaultAddress);
    }

    @Override
    public AddressResponse create(
            AppUser user,
            String label,
            String address,
            DeliveryZone deliveryZone,
            boolean defaultAddress) {
        requireCustomer(user);
        List<UserAddress> existing = addresses(user);
        boolean makeDefault = defaultAddress || existing.isEmpty();
        if (makeDefault) {
            clearDefaults(existing);
        }
        DeliveryZone zone = deliveryZone == null ? DeliveryZone.fromLegacy(address) : deliveryZone;
        return response(
                addressRepository.save(new UserAddress(user, label, address, zone, makeDefault)));
    }

    @Transactional
    @Override
    public AddressResponse update(
            AppUser user, Long id, String label, String address, boolean defaultAddress) {
        return update(user, id, label, address, DeliveryZone.fromLegacy(address), defaultAddress);
    }

    @Override
    public AddressResponse update(
            AppUser user,
            Long id,
            String label,
            String address,
            DeliveryZone deliveryZone,
            boolean defaultAddress) {
        UserAddress target = requireOwned(user, id);
        target.update(
                label,
                address,
                deliveryZone == null ? DeliveryZone.fromLegacy(address) : deliveryZone);
        if (defaultAddress && !target.isDefaultAddress()) {
            clearDefaults(addresses(user));
            target.makeDefault();
        }
        return response(addressRepository.save(target));
    }

    @Transactional
    @Override
    public AddressResponse setDefault(AppUser user, Long id) {
        UserAddress target = requireOwned(user, id);
        clearDefaults(addresses(user));
        target.makeDefault();
        return response(addressRepository.save(target));
    }

    @Transactional
    @Override
    public void delete(AppUser user, Long id) {
        UserAddress target = requireOwned(user, id);
        List<UserAddress> remaining =
                addresses(user).stream()
                        .filter(address -> !address.getId().equals(target.getId()))
                        .toList();
        addressRepository.delete(target);
        if (target.isDefaultAddress() && !remaining.isEmpty()) {
            remaining.get(0).makeDefault();
        }
    }

    private UserAddress requireOwned(AppUser user, Long id) {
        requireCustomer(user);
        UserAddress address =
                addressRepository.findById(id).orElseThrow(() -> new BusinessFailure(404, "地址不存在"));
        if (!user.getId().equals(address.getUser().getId())) {
            throw new BusinessFailure(403, "无权操作此地址");
        }
        return address;
    }

    private List<UserAddress> addresses(AppUser user) {
        return addressRepository.findByUserOrderByDefaultAddressDescUpdatedAtDesc(user);
    }

    private void clearDefaults(List<UserAddress> addresses) {
        addresses.stream().filter(UserAddress::isDefaultAddress).forEach(UserAddress::clearDefault);
    }

    private void requireCustomer(AppUser user) {
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new BusinessFailure(403, "只有用户账号可以管理地址");
        }
    }

    private AddressResponse response(UserAddress address) {
        return new AddressResponse(
                address.getId(),
                address.getLabel(),
                address.getAddress(),
                address.getDeliveryZone(),
                address.isDefaultAddress(),
                address.getCreatedAt(),
                address.getUpdatedAt());
    }
}
