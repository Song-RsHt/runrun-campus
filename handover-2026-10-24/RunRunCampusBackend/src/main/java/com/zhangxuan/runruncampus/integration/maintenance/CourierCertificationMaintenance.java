package com.zhangxuan.runruncampus.integration.maintenance;

import com.zhangxuan.runruncampus.entity.CourierCertification;
import com.zhangxuan.runruncampus.integration.storage.OrderProofStorage;
import com.zhangxuan.runruncampus.repository.CourierCertificationRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;

/** 旧系统骑手资质维护组件；保持当前应用的配置与协作边界。 */
@Component
public class CourierCertificationMaintenance {

    private static final Logger LOG =
            LoggerFactory.getLogger(CourierCertificationMaintenance.class);

    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int PHOTO_RETENTION_DAYS = 30;

    private final CourierCertificationRepository certifications;
    private final OrderProofStorage storage;

    public CourierCertificationMaintenance(
            CourierCertificationRepository certifications, OrderProofStorage storage) {
        this.certifications = certifications;
        this.storage = storage;
    }

    @Transactional
    public void run() {
        run(LocalDate.now(CAMPUS_ZONE));
    }

    public void run(LocalDate today) {
        cleanPhotos(today);
    }

    private void cleanPhotos(LocalDate today) {
        LocalDate cutoff = today.minusDays(PHOTO_RETENTION_DAYS);
        var reviewedBefore = cutoff.plusDays(1).atStartOfDay(CAMPUS_ZONE).toInstant();
        for (CourierCertification certification :
                certifications.findApprovedPhotosToClean(reviewedBefore)) {
            if (delete(certification.getApprovedPhotoKey(), certification.getId())) {
                certification.clearApprovedPhoto();
            }
        }
        for (CourierCertification certification :
                certifications.findRejectedPhotosToClean(reviewedBefore)) {
            if (delete(certification.getSubmittedPhotoKey(), certification.getId())) {
                certification.clearSubmittedPhoto();
            }
        }
    }

    private boolean delete(String key, Long certificationId) {
        try {
            storage.delete(key);
            return true;
        } catch (IOException exception) {
            LOG.warn("学生证材料清理失败，certificationId={}", certificationId, exception);
            return false;
        }
    }
}
