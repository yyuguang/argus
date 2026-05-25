package com.lnzz.argus.system.service;

import com.lnzz.argus.system.entity.SysUser;
import com.lnzz.argus.system.model.ResetPasswordRequest;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.system.model.UserImportResult;
import com.lnzz.argus.system.model.UserRequest;
import com.lnzz.argus.system.model.UserResponse;
import com.lnzz.argus.system.model.UserStatusRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @classname: SysUserAdminService
 * @author: Fantasy
 * @date: 2026/05/16 19:00
 * @description: 后台用户管理接口，负责用户 CRUD、启停、密码重置和导入导出。
 */
public interface SysUserAdminService {

    /**
     * 分页查询后台用户。
     *
     * @param departmentId 部门 ID
     * @param pageNo       页码
     * @param pageSize     每页大小
     * @param username     显示名称
     * @param account      登录账号
     * @param status       用户状态
     * @param roleId       角色 ID
     * @return 用户分页结果
     */
    PageResult<UserResponse> page(String departmentId, Integer pageNo, Integer pageSize,
                                 String username, String account, Integer status, String roleId);

    /**
     * 创建后台用户。
     *
     * @param request 用户请求
     * @return 新用户
     */
    UserResponse create(UserRequest request);

    /**
     * 更新后台用户资料、状态和角色。
     *
     * @param userId  用户 ID
     * @param request 用户请求
     * @return 更新后用户
     */
    UserResponse update(Long userId, UserRequest request);

    /**
     * 批量删除后台用户。
     *
     * @param userIds 用户 ID 列表
     */
    void delete(List<Long> userIds);

    /**
     * 批量更新后台用户状态。
     *
     * @param request 状态更新请求
     */
    void updateStatus(UserStatusRequest request);

    /**
     * 重置用户密码并撤销其历史会话。
     *
     * @param userId  用户 ID
     * @param request 重置密码请求
     */
    void resetPassword(Long userId, ResetPasswordRequest request);

    /**
     * CSV 导入用户。首行字段：
     * account,username,email,phone,departmentId,roleIds,status,password
     *
     * @param file CSV 文件
     * @return 导入结果
     */
    UserImportResult importUsers(MultipartFile file);

    /**
     * 导出后台用户为 CSV。
     *
     * @return CSV 字节内容
     */
    byte[] exportUsers();

    /**
     * 根据 ID 查询用户，不存在时抛出业务异常。
     *
     * @param userId 用户 ID
     * @return 用户实体
     */
    SysUser requireUser(Long userId);
}
