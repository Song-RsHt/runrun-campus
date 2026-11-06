package com.zhangxuan.runruncampus.repository.address;

import com.zhangxuan.runruncampus.model.address.UserAddress;
import com.zhangxuan.runruncampus.model.user.AppUser;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<UserAddress, Long> {

  List<UserAddress> findByUserOrderByDefaultAddressDescUpdatedAtDesc(AppUser user);

  long deleteByUser(AppUser user);
}
