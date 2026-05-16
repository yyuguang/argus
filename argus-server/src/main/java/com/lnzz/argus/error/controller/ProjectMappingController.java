package com.lnzz.argus.error.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.error.entity.ProjectMapping;
import com.lnzz.argus.error.mapper.ProjectMappingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 应用到 SCM 仓库的项目映射管理 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/registry/project-mappings")
@RequiredArgsConstructor
public class ProjectMappingController {

    private final ProjectMappingMapper projectMappingMapper;
    private static final Pattern APP_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{2,100}$");

    @GetMapping
    public Result<List<ProjectMapping>> list(@RequestParam(required = false) String appName,
                                             @RequestParam(required = false) String scmProvider) {
        List<ProjectMapping> mappings = projectMappingMapper.selectList(new LambdaQueryWrapper<ProjectMapping>()
                .eq(hasText(appName), ProjectMapping::getAppName, appName)
                .eq(hasText(scmProvider), ProjectMapping::getScmProvider, scmProvider)
                .orderByAsc(ProjectMapping::getAppName)
                .orderByAsc(ProjectMapping::getId));
        return Result.success(mappings);
    }

    @PostMapping
    public Result<ProjectMapping> create(@RequestBody ProjectMapping request) {
        normalizeAndValidate(request, null);
        request.setId(null);
        projectMappingMapper.insert(request);
        return Result.success("项目映射创建成功", request);
    }

    @PutMapping("/{id}")
    public Result<ProjectMapping> update(@PathVariable Long id, @RequestBody ProjectMapping request) {
        requireById(id);
        normalizeAndValidate(request, id);
        request.setId(id);
        projectMappingMapper.updateById(request);
        ProjectMapping updated = projectMappingMapper.selectById(id);
        return Result.success("项目映射更新成功", updated != null ? updated : request);
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        requireById(id);
        projectMappingMapper.deleteById(id);
        return Result.success("项目映射删除成功", Map.of("id", id));
    }

    private ProjectMapping requireById(Long id) {
        ProjectMapping mapping = projectMappingMapper.selectById(id);
        if (mapping == null) {
            throw new BizException(ResultCode.NOT_FOUND, "项目映射不存在: " + id);
        }
        return mapping;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void normalizeAndValidate(ProjectMapping request, Long currentId) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目映射不能为空");
        }
        request.setAppName(trimToNull(request.getAppName()));
        request.setScmProvider(trimToNull(request.getScmProvider()));
        if (request.getScmProvider() != null) {
            request.setScmProvider(request.getScmProvider().toLowerCase());
        }
        request.setSourceRoot(trimToNull(request.getSourceRoot()));
        request.setBasePackage(trimToNull(request.getBasePackage()));
        request.setDefaultBranch(trimToNull(request.getDefaultBranch()));

        if (!StringUtils.hasText(request.getAppName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "appName 不能为空");
        }
        if (!APP_NAME_PATTERN.matcher(request.getAppName()).matches()) {
            throw new BizException(ResultCode.PARAM_ERROR, "appName 仅支持字母、数字、下划线、中划线和点号，长度 2-100");
        }
        if (!StringUtils.hasText(request.getScmProvider()) || request.getScmProjectId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "SCM 平台和项目 ID 不能为空");
        }
        if (!StringUtils.hasText(request.getSourceRoot())) {
            throw new BizException(ResultCode.PARAM_ERROR, "服务源码根不能为空");
        }
        if (request.getSourceRoot().startsWith("/")) {
            throw new BizException(ResultCode.PARAM_ERROR, "服务源码根不能以 / 开头");
        }
        if (!StringUtils.hasText(request.getDefaultBranch())) {
            throw new BizException(ResultCode.PARAM_ERROR, "默认分支不能为空");
        }

        ProjectMapping duplicate = projectMappingMapper.selectOne(new LambdaQueryWrapper<ProjectMapping>()
                .eq(ProjectMapping::getAppName, request.getAppName())
                .ne(currentId != null, ProjectMapping::getId, currentId)
                .last("LIMIT 1"));
        if (duplicate != null) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "appName " + request.getAppName() + " 已存在，一个 appName 只能绑定一个服务源码位置");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
