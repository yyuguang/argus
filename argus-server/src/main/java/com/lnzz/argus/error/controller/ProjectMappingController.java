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

import java.util.List;
import java.util.Map;

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
        request.setId(null);
        projectMappingMapper.insert(request);
        return Result.success("项目映射创建成功", request);
    }

    @PutMapping("/{id}")
    public Result<ProjectMapping> update(@PathVariable Long id, @RequestBody ProjectMapping request) {
        requireById(id);
        request.setId(id);
        projectMappingMapper.updateById(request);
        return Result.success("项目映射更新成功", projectMappingMapper.selectById(id));
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
}
