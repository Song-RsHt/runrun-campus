package com.zhangxuan.runruncampus.repository.address;

import com.zhangxuan.runruncampus.entity.address.UserAddress;
import com.zhangxuan.runruncampus.entity.user.AppUser;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 收件地址 JPA 仓储；只定义持久化查询和写入，不承担权限及业务决策。 */
public interface AddressRepository extends JpaRepository<UserAddress, Long> {

    List<UserAddress> findByUserOrderByDefaultAddressDescUpdatedAtDesc(AppUser user);

    long deleteByUser(AppUser user);
}
