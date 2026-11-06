package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.model.CertificationPhoto;
import com.zhangxuan.runruncampus.model.UploadSource;
import com.zhangxuan.runruncampus.vo.CertificationResponse;

import java.util.List;

/** 旧系统骑手资质业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface CourierCertificationService {

    /**
     * 查询旧系统骑手资质概览。
     *
     * @param courier 当前骑手账号或标识
     * @return 资质
     */
    CertificationResponse overview(AppUser courier);

    /**
     * 提交旧系统骑手资质资料。
     *
     * @param courier 当前骑手账号或标识
     * @return 资质
     */
    CertificationResponse submit(AppUser courier);

    /**
     * 提交旧系统骑手资质资料。
     *
     * @param courier 当前骑手账号或标识
     * @param expectedVersion 调用方看到的并发版本，更新必须与当前版本一致
     * @return 资质
     */
    CertificationResponse submit(AppUser courier, Long expectedVersion);

    /**
     * 判断骑手当前是否具备接单资格。
     *
     * @param courier 当前骑手账号或标识
     * @return 骑手当前是否具备有效接单资格
     */
    boolean isEligible(AppUser courier);

    /**
     * 查询待审核的骑手资质申请。
     *
     * @param admin 当前管理员账号
     * @return 资质列表，顺序和筛选沿用原查询
     */
    List<CertificationResponse> pending(AppUser admin);

    /**
     * 批准骑手资质，核对当前申请和审核凭证。
     *
     * @param admin 当前管理员账号
     * @param id 目标业务记录标识
     * @return 资质
     */
    CertificationResponse approve(AppUser admin, Long id);

    /**
     * 批准骑手资质，核对当前申请和审核凭证。
     *
     * @param admin 当前管理员账号
     * @param id 目标业务记录标识
     * @param expectedVersion 调用方看到的并发版本，更新必须与当前版本一致
     * @param photoResourceId 本次审核所确认的图片资源标识
     * @return 资质
     */
    CertificationResponse approve(
            AppUser admin, Long id, long expectedVersion, String photoResourceId);

    /**
     * 驳回骑手资质申请并保存原因。
     *
     * @param admin 当前管理员账号
     * @param id 目标业务记录标识
     * @param reason 业务操作理由，按原有长度和必填规则校验
     * @return 资质
     */
    CertificationResponse reject(AppUser admin, Long id, String reason);

    /**
     * 校验并保存私有学生证图片，文件内容在鉴权和状态检查后读取。
     *
     * <p>文件读取发生在原鉴权和状态检查之后；替换图片按原保留期删除，不能立即移除旧证据。
     *
     * @param courier 当前骑手账号或标识
     * @param id 目标业务记录标识
     * @param file 延迟读取的上传文件；业务校验后才读取内容
     * @return 资质
     */
    CertificationResponse uploadStudentCard(AppUser courier, Long id, UploadSource file);

    /**
     * 校验并保存私有学生证图片，文件内容在鉴权和状态检查后读取。
     *
     * <p>文件读取发生在原鉴权和状态检查之后；替换图片按原保留期删除，不能立即移除旧证据。
     *
     * @param courier 当前骑手账号或标识
     * @param id 目标业务记录标识
     * @param file 延迟读取的上传文件；业务校验后才读取内容
     * @param expectedVersion 调用方看到的并发版本，更新必须与当前版本一致
     * @return 资质
     */
    CertificationResponse uploadStudentCard(
            AppUser courier, Long id, UploadSource file, Long expectedVersion);

    /**
     * 授权读取学生证图片及其内容类型。
     *
     * @param admin 当前管理员账号
     * @param id 目标业务记录标识
     * @return 资质图片
     */
    CertificationPhoto studentCardPhoto(AppUser admin, Long id);

    /**
     * 注销时移除可直接识别个人的资质数据，按保留策略处理证据。
     *
     * @param courier 当前骑手账号或标识
     */
    void deletePersonalData(AppUser courier);
}
