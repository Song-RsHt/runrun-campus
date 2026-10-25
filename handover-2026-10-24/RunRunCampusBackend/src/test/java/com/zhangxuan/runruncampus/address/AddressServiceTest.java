package com.zhangxuan.runruncampus.address;

import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import com.zhangxuan.runruncampus.order.DeliveryZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {
    @Mock
    private AddressRepository addressRepository;

    private AddressService addressService;
    private AppUser customer;

    @BeforeEach
    void setUp() {
        addressService = new AddressService(addressRepository);
        customer = user(1L, "13800000001");
    }

    @Test
    void firstAddressBecomesDefault() {
        when(addressRepository.findByUserOrderByDefaultAddressDescUpdatedAtDesc(customer)).thenReturn(List.of());
        when(addressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressService.AddressResponse response = addressService.create(customer, "宿舍", "3号楼 205", false);

        assertTrue(response.defaultAddress());
    }

    @Test
    void addressKeepsExplicitDeliveryZone() {
        when(addressRepository.findByUserOrderByDefaultAddressDescUpdatedAtDesc(customer)).thenReturn(List.of());
        when(addressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressService.AddressResponse response = addressService.create(
                customer, "宿舍", "梅园字样但属于其他区域", DeliveryZone.STANDARD, false);

        assertEquals(DeliveryZone.STANDARD, response.deliveryZone());
    }

    @Test
    void settingDefaultClearsPreviousDefault() {
        UserAddress previous = address(11L, customer, "宿舍", "3号楼 205", true);
        UserAddress next = address(12L, customer, "实验室", "信息楼 401", false);
        when(addressRepository.findById(12L)).thenReturn(Optional.of(next));
        when(addressRepository.findByUserOrderByDefaultAddressDescUpdatedAtDesc(customer))
                .thenReturn(List.of(previous, next));
        when(addressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressService.AddressResponse response = addressService.setDefault(customer, 12L);

        assertFalse(previous.isDefaultAddress());
        assertTrue(response.defaultAddress());
    }

    @Test
    void customerCannotUpdateAnotherUsersAddress() {
        UserAddress address = address(11L, customer, "宿舍", "3号楼 205", true);
        AppUser other = user(2L, "13800000002");
        when(addressRepository.findById(11L)).thenReturn(Optional.of(address));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> addressService.update(other, 11L, "新地址", "4号楼 301", false));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("3号楼 205", address.getAddress());
    }

    @Test
    void deletingDefaultPromotesRemainingAddress() {
        UserAddress current = address(11L, customer, "宿舍", "3号楼 205", true);
        UserAddress remaining = address(12L, customer, "实验室", "信息楼 401", false);
        when(addressRepository.findById(11L)).thenReturn(Optional.of(current));
        when(addressRepository.findByUserOrderByDefaultAddressDescUpdatedAtDesc(customer))
                .thenReturn(List.of(current, remaining));

        addressService.delete(customer, 11L);

        assertTrue(remaining.isDefaultAddress());
    }

    private AppUser user(Long id, String phone) {
        AppUser user = new AppUser(phone, "password-hash", "测试用户", UserRole.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserAddress address(Long id, AppUser owner, String label, String detail, boolean defaultAddress) {
        UserAddress address = new UserAddress(owner, label, detail, defaultAddress);
        ReflectionTestUtils.setField(address, "id", id);
        return address;
    }
}
