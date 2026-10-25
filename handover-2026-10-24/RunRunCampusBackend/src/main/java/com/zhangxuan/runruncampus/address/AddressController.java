package com.zhangxuan.runruncampus.address;

import com.zhangxuan.runruncampus.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.zhangxuan.runruncampus.order.DeliveryZone;

@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController {
    private final AuthService authService;
    private final AddressService addressService;

    public AddressController(AuthService authService, AddressService addressService) {
        this.authService = authService;
        this.addressService = addressService;
    }

    @GetMapping
    List<AddressService.AddressResponse> list(@RequestHeader("Authorization") String authorization) {
        return addressService.list(authService.requireUser(authorization));
    }

    @PostMapping
    AddressService.AddressResponse create(@RequestHeader("Authorization") String authorization,
                                          @Valid @RequestBody AddressRequest request) {
        return addressService.create(authService.requireUser(authorization), request.label(), request.address(),
                request.deliveryZone(), request.defaultAddress());
    }

    @PutMapping("/{id}")
    AddressService.AddressResponse update(@RequestHeader("Authorization") String authorization,
                                          @PathVariable Long id,
                                          @Valid @RequestBody AddressRequest request) {
        return addressService.update(authService.requireUser(authorization), id, request.label(), request.address(),
                request.deliveryZone(), request.defaultAddress());
    }

    @PostMapping("/{id}/default")
    AddressService.AddressResponse setDefault(@RequestHeader("Authorization") String authorization,
                                              @PathVariable Long id) {
        return addressService.setDefault(authService.requireUser(authorization), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        addressService.delete(authService.requireUser(authorization), id);
    }

    public record AddressRequest(
            @NotBlank(message = "请输入地址标签") @Size(max = 32, message = "地址标签过长") String label,
            @NotBlank(message = "请输入详细地址") @Size(max = 120, message = "详细地址过长") String address,
            DeliveryZone deliveryZone,
            boolean defaultAddress
    ) {
        public AddressRequest(String label, String address, boolean defaultAddress) {
            this(label, address, DeliveryZone.fromLegacy(address), defaultAddress);
        }
    }
}
