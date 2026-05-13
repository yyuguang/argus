package com.lnzz.argus.error.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.error.entity.ProjectMapping;
import com.lnzz.argus.error.mapper.ProjectMappingMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProjectMappingController - 项目映射管理")
class ProjectMappingControllerTest {

    @Test
    @DisplayName("创建项目映射时清空请求中的 id")
    void createClearsRequestId() {
        ProjectMappingMapper mapper = mock(ProjectMappingMapper.class);
        ProjectMappingController controller = new ProjectMappingController(mapper);
        ProjectMapping request = new ProjectMapping();
        request.setId(99L);
        request.setAppName("order-service");

        Result<ProjectMapping> result = controller.create(request);

        assertEquals(null, result.getData().getId());
        verify(mapper).insert(any(ProjectMapping.class));
    }

    @Test
    @DisplayName("创建微服务映射不限制同一个 SCM 项目只能绑定一个 appName")
    void createAllowsMultipleAppsOnSameScmProject() {
        ProjectMappingMapper mapper = mock(ProjectMappingMapper.class);
        ProjectMappingController controller = new ProjectMappingController(mapper);
        ProjectMapping request = new ProjectMapping();
        request.setAppName("payment-service");
        request.setScmProvider("github");
        request.setScmProjectId(100L);
        request.setSourceRoot("payment-service/src/main/java");
        request.setBasePackage("com.example.payment");

        Result<ProjectMapping> result = controller.create(request);

        assertEquals("payment-service", result.getData().getAppName());
        assertEquals(100L, result.getData().getScmProjectId());
        verify(mapper).insert(any(ProjectMapping.class));
    }

    @Test
    @DisplayName("删除项目映射前校验存在")
    void deleteRequiresExistingMapping() {
        ProjectMappingMapper mapper = mock(ProjectMappingMapper.class);
        ProjectMappingController controller = new ProjectMappingController(mapper);
        ProjectMapping mapping = new ProjectMapping();
        mapping.setId(1L);
        when(mapper.selectById(1L)).thenReturn(mapping);

        Result<Map<String, Object>> result = controller.delete(1L);

        assertEquals(1L, result.getData().get("id"));
        verify(mapper).deleteById(1L);
    }
}
