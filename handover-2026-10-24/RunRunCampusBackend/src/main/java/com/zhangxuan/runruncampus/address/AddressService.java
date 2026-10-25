package com.zhangxuan.runruncampus.address;

import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import com.zhangxuan.runruncampus.order.DeliveryZone;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class AddressService {
    private final AddressRepository addressRepository;

    public AddressService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(AppUser user) {
        requireCustomer(user);
        return addresses(user).stream().map(this::response).toList();
    }

    @Transactional
    public AddressResponse create(AppUser user, String label, String address, boolean defaultAddress) {
        return create(user, label, address, DeliveryZone.fromLegacy(address), defaultAddress);
    }

    public AddressResponse create(AppUser user, String label, String address, DeliveryZone deliveryZone, boolean defaultAddress) {
        requireCustomer(user);
        List<UserAddress> existing = addresses(user);
        boolean makeDefault = defaultAddress || existing.isEmpty();
        if (makeDefault) clearDefaults(existing);
        DeliveryZone zone = deliveryZone == null ? DeliveryZone.fromLegacy(address) : deliveryZone;
        return response(addressRepository.save(new UserAddress(user, label, address, zone, makeDefault)));
    }

    @Transactional
    public AddressResponse update(AppUser user, Long id, String label, String address, boolean defaultAddress) {
        return update(user, id, label, address, DeliveryZone.fromLegacy(address), defaultAddress);
    }

    public AddressResponse update(AppUser user, Long id, String label, String address, DeliveryZone deliveryZone, boolean defaultAddress) {
        UserAddress target = requireOwned(user, id);
        target.update(label, address, deliveryZone == null ? DeliveryZone.fromLegacy(address) : deliveryZone);
        if (defaultAddress && !target.isDefaultAddress()) {
            clearDefaults(addresses(user));
            target.makeDefault();
        }
        return response(addressRepository.save(target));
    }

    @Transactional
    public AddressResponse setDefault(AppUser user, Long id) {
        UserAddress target = requireOwned(user, id);
        clearDefaults(addresses(user));
        target.makeDefault();
        return response(addressRepository.save(target));
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        UserAddress target = requireOwned(user, id);
        List<UserAddress> remaining = addresses(user).stream()
                .filter(address -> !address.getId().equals(target.getId()))
                .toList();
        addressRepository.delete(target);
        if (target.isDefaultAddress() && !remaining.isEmpty()) {
            remaining.get(0).makeDefault();
        }
    }

    private UserAddress requireOwned(AppUser user, Long id) {
        requireCustomer(user);
        UserAddress address = addressRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "地址不存在"));
        if (!user.getId().equals(address.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作此地址");
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有用户账号可以管理地址");
        }
    }

    private AddressResponse response(UserAddress address) {
        return new AddressResponse(address.getId(), address.getLabel(), address.getAddress(),
                address.getDeliveryZone(), address.isDefaultAddress(), address.getCreatedAt(), address.getUpdatedAt());
    }

    public record AddressResponse(Long id, String label, String address, DeliveryZone deliveryZone, boolean defaultAddress,
                                  Instant createdAt, Instant updatedAt) { }
}
