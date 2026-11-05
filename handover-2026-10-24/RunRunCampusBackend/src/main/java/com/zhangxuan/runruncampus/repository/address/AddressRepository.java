package com.zhangxuan.runruncampus.repository.address;
import com.zhangxuan.runruncampus.model.address.UserAddress;

import com.zhangxuan.runruncampus.model.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<UserAddress, Long> {
    List<UserAddress> findByUserOrderByDefaultAddressDescUpdatedAtDesc(AppUser user);
    long deleteByUser(AppUser user);
}
