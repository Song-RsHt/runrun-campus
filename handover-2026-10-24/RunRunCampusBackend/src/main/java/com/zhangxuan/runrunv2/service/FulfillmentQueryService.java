package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.mapper.FulfillmentQueryMapper;
import com.zhangxuan.runrunv2.model.ResourceIds;
import com.zhangxuan.runrunv2.model.TaskRow;
import com.zhangxuan.runrunv2.security.IdentitySecrets;
import com.zhangxuan.runrunv2.vo.CourierTaskVO;
import com.zhangxuan.runrunv2.vo.CursorPageVO;
import com.zhangxuan.runrunv2.vo.FulfillmentOrderVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface FulfillmentQueryService {

    CursorPageVO<CourierTaskVO> tasks(String token, String cursor, Integer requestedLimit, String status);

    CursorPageVO<FulfillmentOrderVO> pool(String token, String cursor, Integer requestedLimit);
}
