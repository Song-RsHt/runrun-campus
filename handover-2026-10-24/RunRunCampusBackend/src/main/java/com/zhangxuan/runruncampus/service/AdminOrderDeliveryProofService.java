package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.enums.OrderProofType;
import com.zhangxuan.runruncampus.integration.storage.OrderProofStorage;
import com.zhangxuan.runruncampus.repository.OrderProofRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.vo.ProofContent;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminOrderDeliveryProofService {

    ProofContent read(Long id);
}
