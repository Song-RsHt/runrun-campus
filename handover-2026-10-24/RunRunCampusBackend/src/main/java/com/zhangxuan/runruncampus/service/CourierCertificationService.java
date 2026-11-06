package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.model.CertificationPhoto;
import com.zhangxuan.runruncampus.model.UploadSource;
import com.zhangxuan.runruncampus.vo.CertificationResponse;
import java.util.List;






























/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface CourierCertificationService {

    CertificationResponse overview(AppUser courier);

    CertificationResponse submit(AppUser courier);

    CertificationResponse submit(AppUser courier, Long expectedVersion);

    boolean isEligible(AppUser courier);

    List<CertificationResponse> pending(AppUser admin);

    CertificationResponse approve(AppUser admin, Long id);

    CertificationResponse approve(AppUser admin, Long id, long expectedVersion, String photoResourceId);

    CertificationResponse reject(AppUser admin, Long id, String reason);

    CertificationResponse uploadStudentCard(AppUser courier, Long id, UploadSource file);

    CertificationResponse uploadStudentCard(AppUser courier, Long id, UploadSource file, Long expectedVersion);

    CertificationPhoto studentCardPhoto(AppUser admin, Long id);

    void deletePersonalData(AppUser courier);
}
