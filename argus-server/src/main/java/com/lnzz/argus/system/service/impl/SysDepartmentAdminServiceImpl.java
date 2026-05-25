package com.lnzz.argus.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.common.enums.SecurityAuditResourceType;
import com.lnzz.argus.common.enums.SysStatus;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.security.LoginUtil;
import com.lnzz.argus.system.entity.SysDepartment;
import com.lnzz.argus.system.mapper.SysDepartmentMapper;
import com.lnzz.argus.system.mapper.SysUserMapper;
import com.lnzz.argus.system.model.DepartmentRequest;
import com.lnzz.argus.system.model.DepartmentResponse;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.system.service.SecurityAuditService;
import com.lnzz.argus.system.service.SysDepartmentAdminService;
import com.lnzz.argus.system.support.SystemAdminSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 部门管理服务。
 * <p>负责部门树、分页、创建、修改和软删除校验。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDepartmentAdminServiceImpl implements SysDepartmentAdminService {

    private final SysDepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;
    private final SecurityAuditService auditService;

    /**
     * 查询部门树。
     *
     * @return 部门树
     */
    @Override
    public List<DepartmentResponse> tree() {
        List<SysDepartment> departments = departmentMapper.selectNonDeletedOrdered();
        return buildChildren(null, departments);
    }

    /**
     * 分页查询部门列表。
     *
     * @param pageNo         页码
     * @param pageSize       每页大小
     * @param departmentName 部门名称
     * @param status         状态
     * @return 分页结果
     */
    @Override
    public PageResult<DepartmentResponse> page(Integer pageNo, Integer pageSize, String departmentName, Integer status) {
        int normalizedPageNo = SystemAdminSupport.pageNo(pageNo);
        int normalizedPageSize = SystemAdminSupport.pageSize(pageSize);
        Page<SysDepartment> page = departmentMapper.selectAdminPage(
                new Page<>(normalizedPageNo, normalizedPageSize),
                departmentName,
                status == null ? null : SysStatus.fromApi(status));
        return PageResult.of(
                page.getRecords().stream().map(item -> toResponse(item, List.of())).toList(),
                page.getCurrent(),
                page.getSize(),
                page.getTotal());
    }

    /**
     * 创建部门。
     *
     * @param request 部门请求
     * @return 新部门
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepartmentResponse create(DepartmentRequest request) {
        validate(request);
        SysDepartment department = new SysDepartment();
        applyRequest(department, request);
        department.setDepartmentCode("DEPT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        department.setIsDeleted(SystemAdminSupport.NOT_DELETED);
        department.setVersion(0);
        departmentMapper.insert(department);
        auditService.success("DEPARTMENT_CREATE", SecurityAuditResourceType.DEPARTMENT, department.getId(), null, department);
        log.info("创建部门: departmentId={}, departmentName={}, parentId={}, status={}",
                department.getId(), department.getDepartmentName(), department.getParentId(), department.getStatus());
        return toResponse(department, List.of());
    }

    /**
     * 更新部门。
     *
     * @param departmentId 部门 ID
     * @param request      部门请求
     * @return 更新后部门
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepartmentResponse update(Long departmentId, DepartmentRequest request) {
        SysDepartment department = requireDepartment(departmentId);
        validate(request);
        SysDepartment before = copy(department);
        applyRequest(department, request);
        departmentMapper.updateById(department);
        auditService.success("DEPARTMENT_UPDATE", SecurityAuditResourceType.DEPARTMENT, departmentId, before, department);
        log.info("更新部门: departmentId={}, departmentName={}, status={}",
                departmentId, department.getDepartmentName(), department.getStatus());
        return toResponse(department, List.of());
    }

    /**
     * 删除部门。
     *
     * @param ids 部门 ID 列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "部门 ID 不能为空");
        }
        for (Long id : ids) {
            SysDepartment department = requireDepartment(id);
            long childCount = departmentMapper.countNonDeletedChildren(id);
            if (childCount > 0) {
                log.warn("删除部门被拒绝: departmentId={}, departmentName={}, reason=存在子部门",
                        id, department.getDepartmentName());
                throw new BizException(ResultCode.PARAM_ERROR, "部门存在子部门，不能删除: " + department.getDepartmentName());
            }
            long userCount = userMapper.countNonDeletedByDepartmentId(id);
            if (userCount > 0) {
                log.warn("删除部门被拒绝: departmentId={}, departmentName={}, reason=存在用户",
                        id, department.getDepartmentName());
                throw new BizException(ResultCode.PARAM_ERROR, "部门下存在用户，不能删除: " + department.getDepartmentName());
            }
            int deletedRows = departmentMapper.softDeleteById(id, LoginUtil.currentUsernameOrSystem());
            if (deletedRows != 1) {
                log.warn("删除部门失败: departmentId={}, departmentName={}, reason=数据已变化",
                        id, department.getDepartmentName());
                throw new BizException(ResultCode.NOT_FOUND, "部门不存在或已被删除: " + department.getDepartmentName());
            }
            auditService.success("DEPARTMENT_DELETE", SecurityAuditResourceType.DEPARTMENT, id, department, null);
            log.info("删除部门: departmentId={}, departmentName={}", id, department.getDepartmentName());
        }
    }

    /**
     * 根据 ID 查询部门，不存在时抛出业务异常。
     *
     * @param id 部门 ID
     * @return 部门实体
     */
    @Override
    public SysDepartment requireDepartment(Long id) {
        SysDepartment department = departmentMapper.selectNonDeletedById(id);
        if (department == null) {
            throw new BizException(ResultCode.NOT_FOUND, "部门不存在: " + id);
        }
        return department;
    }

    private List<DepartmentResponse> buildChildren(Long parentId, List<SysDepartment> departments) {
        return departments.stream()
                .filter(item -> java.util.Objects.equals(parentId, item.getParentId()))
                .sorted(Comparator.comparing(SysDepartment::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SysDepartment::getId))
                .map(item -> toResponse(item, buildChildren(item.getId(), departments)))
                .toList();
    }

    private DepartmentResponse toResponse(SysDepartment department, List<DepartmentResponse> children) {
        return new DepartmentResponse(
                SystemAdminSupport.stringId(department.getId()),
                SystemAdminSupport.stringId(department.getParentId()),
                department.getDepartmentName(),
                SysStatus.toApi(department.getStatus()),
                department.getSortOrder(),
                SystemAdminSupport.format(department.getCreateTime()),
                department.getRemark(),
                children == null || children.isEmpty() ? null : children);
    }

    private void validate(DepartmentRequest request) {
        if (request == null || !StringUtils.hasText(request.departmentName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "部门名称不能为空");
        }
    }

    private void applyRequest(SysDepartment department, DepartmentRequest request) {
        department.setParentId(SystemAdminSupport.parseId(request.parentId(), "父部门ID"));
        department.setDepartmentName(request.departmentName().trim());
        department.setStatus(SysStatus.fromApi(request.status()));
        department.setRemark(SystemAdminSupport.trimToNull(request.remark()));
        department.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private SysDepartment copy(SysDepartment source) {
        SysDepartment target = new SysDepartment();
        target.setId(source.getId());
        target.setParentId(source.getParentId());
        target.setDepartmentName(source.getDepartmentName());
        target.setDepartmentCode(source.getDepartmentCode());
        target.setStatus(source.getStatus());
        target.setSortOrder(source.getSortOrder());
        target.setRemark(source.getRemark());
        return target;
    }
}
