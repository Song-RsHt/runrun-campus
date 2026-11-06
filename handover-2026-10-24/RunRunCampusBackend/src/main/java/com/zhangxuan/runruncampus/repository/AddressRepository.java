package com.zhangxuan.runruncampus.repository;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserAddress;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;







public interface AddressRepository extends JpaRepository<UserAddress, Long> {

    List<UserAddress> findByUserOrderByDefaultAddressDescUpdatedAtDesc(AppUser user);

    long deleteByUser(AppUser user);
}
