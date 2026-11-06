package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserMembership;
import com.zhangxuan.runruncampus.enums.MembershipPlanType;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.model.MembershipApplication;
import com.zhangxuan.runruncampus.repository.UserMembershipRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

public record MembershipPlanVO(MembershipPlanType type, String title, int trips, int priceCents, String note) {
}
