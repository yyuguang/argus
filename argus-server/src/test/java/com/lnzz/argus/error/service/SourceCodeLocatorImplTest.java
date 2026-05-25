package com.lnzz.argus.error.service;

import com.lnzz.argus.config.ErrorProcessingProperties;
import com.lnzz.argus.codeindex.dto.req.SourceLocateReqDTO;
import com.lnzz.argus.codeindex.dto.res.SourceLocateResDTO;
import com.lnzz.argus.codeindex.service.SourceLocationService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.error.entity.ProjectMapping;
import com.lnzz.argus.error.mapper.ProjectMappingMapper;
import com.lnzz.argus.error.service.impl.SourceCodeLocatorImpl;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.mapper.ScmConfigMapper;
import com.lnzz.argus.scm.service.ScmPlatformService;
import com.lnzz.argus.scm.service.ScmPlatformServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SourceCodeLocatorImpl - 源码缓存与降级")
class SourceCodeLocatorImplTest {

    @Mock
    private ProjectMappingMapper projectMappingMapper;
    @Mock
    private ScmConfigMapper scmConfigMapper;
    @Mock
    private ScmPlatformServiceFactory scmFactory;
    @Mock
    private ScmPlatformService scmService;
    @Mock
    private SourceLocationService sourceLocationService;

    private InMemorySourceFileCache cache;
    private ErrorProcessingProperties properties;
    private SourceCodeLocatorImpl locator;

    @BeforeEach
    void setUp() {
        cache = new InMemorySourceFileCache();
        properties = new ErrorProcessingProperties();
        locator = new SourceCodeLocatorImpl(projectMappingMapper, scmConfigMapper, scmFactory, cache, properties,
                sourceLocationService);
    }

    @Test
    @DisplayName("索引命中时优先按索引 filePath 和 commit 拉取源码")
    void locatePrefersCodeIndexLocation() {
        givenMappingAndConfig();
        when(sourceLocationService.locate(any(SourceLocateReqDTO.class))).thenReturn(indexedLocation());
        when(scmService.getFileContent(any(), eq("service/src/main/java/com/example/DemoService.java"), eq("idx123")))
                .thenReturn("class DemoServiceByIndex {}");

        SourceCodeLocator.SourceLocation location = locator.locate(appEvent());

        assertTrue(location.found());
        assertEquals("service/src/main/java/com/example/DemoService.java", location.filePath());
        assertEquals("class DemoServiceByIndex {}", location.content());
        verify(scmService, never()).getFileContent(any(), eq("src/main/java/com/example/DemoService.java"), eq("main"));
    }

    @Test
    @DisplayName("定位源码时通过缓存读取 SCM 单文件")
    void locateUsesSourceCache() {
        givenMappingAndConfig();
        when(scmService.getFileContent(any(), eq("src/main/java/com/example/DemoService.java"), eq("main")))
                .thenReturn("class DemoService {}");

        SourceCodeLocator.SourceLocation location = locator.locate(appEvent());

        assertTrue(location.found());
        assertEquals("class DemoService {}", location.content());
        assertEquals(1, cache.loadCount("main|src/main/java/com/example/DemoService.java"));
    }

    @Test
    @DisplayName("SCM 拉取失败时降级为未找到，不中断分析链路")
    void scmFailureFallsBackToNotFound() {
        givenMappingAndConfig();
        when(scmService.getFileContent(any(), any(), eq("main"))).thenThrow(new RuntimeException("SCM down"));

        SourceCodeLocator.SourceLocation location = locator.locate(appEvent());

        assertFalse(location.found());
        assertTrue(location.reason().contains("源码定位失败"));
    }

    @Test
    @DisplayName("Prompt 源码总预算会截断主文件内容")
    void promptSourceBudgetTruncatesContent() {
        properties.getSource().setMaxPromptSourceChars(5);
        givenMappingAndConfig();
        when(scmService.getFileContent(any(), eq("src/main/java/com/example/DemoService.java"), eq("main")))
                .thenReturn("123456789");

        SourceCodeLocator.SourceLocation location = locator.locate(appEvent());

        assertEquals("12345", location.content());
    }

    @Test
    @DisplayName("索引未命中时 Nginx 降级逻辑不受影响")
    void nginxFallbackStillWorksWhenIndexMissed() {
        givenMappingAndConfig();
        when(sourceLocationService.locate(any(SourceLocateReqDTO.class))).thenReturn(notMatchedLocation());
        when(scmService.getFileContent(any(), eq("src/main/resources/application.yml"), eq("main")))
                .thenReturn("server:\n  port: 8080");

        SourceCodeLocator.SourceLocation location = locator.locate(nginxEvent());

        assertTrue(location.found());
        assertEquals("src/main/resources/application.yml", location.filePath());
        assertEquals("Nginx降级", location.reason());
    }

    private void givenMappingAndConfig() {
        ProjectMapping mapping = new ProjectMapping();
        mapping.setAppName("order-service");
        mapping.setScmProvider("github");
        mapping.setScmProjectId(100L);
        mapping.setSourceRoot("src/main/java");
        mapping.setBasePackage("com.example");
        mapping.setDefaultBranch("main");

        ScmConfig scmConfig = new ScmConfig();
        scmConfig.setId(1L);
        scmConfig.setScmProvider("github");
        scmConfig.setProjectId(100L);
        scmConfig.setEnabled(true);
        scmConfig.setMaxRelatedClasses(0);

        when(projectMappingMapper.selectOne(any())).thenReturn(mapping);
        when(scmConfigMapper.selectOne(any())).thenReturn(scmConfig);
        when(scmFactory.getRequired("github")).thenReturn(scmService);
    }

    private ErrorEvent appEvent() {
        ErrorEvent event = new ErrorEvent();
        event.setAppName("order-service");
        event.setClassName("com.example.DemoService");
        event.setLineNumber(10);
        return event;
    }

    private ErrorEvent nginxEvent() {
        ErrorEvent event = new ErrorEvent();
        event.setAppName("order-service");
        event.setEnvironment("prod");
        event.setSourceType("NGINX");
        event.setInterfaceRef("/api/order/list");
        return event;
    }

    private SourceLocateResDTO indexedLocation() {
        SourceLocateResDTO response = new SourceLocateResDTO();
        response.setMatched(true);
        response.setConfidence(CodeIndexConstants.Confidence.HIGH);
        response.setMatchType(CodeIndexConstants.MatchType.QUALIFIED_NAME);
        response.setIndexId(99L);
        response.setCommitSha("idx123");
        response.setSourceRoot("service/src/main/java");
        response.setFilePath("service/src/main/java/com/example/DemoService.java");
        response.setQualifiedName("com.example.DemoService");
        return response;
    }

    private SourceLocateResDTO notMatchedLocation() {
        SourceLocateResDTO response = new SourceLocateResDTO();
        response.setMatched(false);
        response.setConfidence(CodeIndexConstants.Confidence.NONE);
        response.setMatchType(CodeIndexConstants.MatchType.NONE);
        return response;
    }

    private static class InMemorySourceFileCache implements SourceFileCacheService {
        private final Map<String, String> contentCache = new ConcurrentHashMap<>();
        private final Map<String, Integer> counts = new ConcurrentHashMap<>();

        @Override
        public String getOrLoad(ScmConfig config, String ref, String filePath, Supplier<String> loader) {
            String key = ref + "|" + filePath;
            if (contentCache.containsKey(key)) {
                return contentCache.get(key);
            }
            counts.merge(key, 1, Integer::sum);
            String content = loader.get();
            contentCache.put(key, content);
            return content;
        }

        @Override
        public void clear() {
            contentCache.clear();
        }

        private int loadCount(String key) {
            return counts.getOrDefault(key, 0);
        }
    }
}
