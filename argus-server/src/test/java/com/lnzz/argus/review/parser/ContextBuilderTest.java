package com.lnzz.argus.review.parser;

import com.lnzz.argus.codeindex.dto.req.SourceLocateReqDTO;
import com.lnzz.argus.codeindex.dto.res.SourceLocateResDTO;
import com.lnzz.argus.codeindex.service.SourceLocationService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.DiffFile;
import com.lnzz.argus.scm.service.ScmPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContextBuilder - 索引关联类解析")
class ContextBuilderTest {

    @Mock
    private SourceLocationService sourceLocationService;
    @Mock
    private ScmPlatformService scmService;

    private ContextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ContextBuilder(new DiffParser(), sourceLocationService);
    }

    @Test
    @DisplayName("索引命中时不依赖人工 basePackages 也能拉取关联类摘要")
    void indexHitResolvesRelatedClassWithoutBasePackages() {
        ScmConfig config = config(99L, null, null, null, 5);
        ReviewTask task = task("abc1234");
        DiffFile diff = diff("order-service/src/main/java/com/acme/order/OrderService.java");
        String fullContent = """
                package com.acme.order;

                import java.util.List;
                import com.acme.common.UserDTO;

                public class OrderService {
                    private UserDTO user;
                }
                """;
        String relatedPath = "common/src/main/java/com/acme/common/UserDTO.java";

        when(scmService.getFileContent(config, task, diff.getNewPath(), "abc1234")).thenReturn(fullContent);
        when(sourceLocationService.locate(any(SourceLocateReqDTO.class))).thenAnswer(invocation -> {
            SourceLocateReqDTO request = invocation.getArgument(0);
            if ("com.acme.common.UserDTO".equals(request.getQualifiedName())) {
                return matched("com.acme.common.UserDTO", relatedPath, "abc1234");
            }
            return notMatched();
        });
        when(scmService.getFileContent(config, task, relatedPath, "abc1234")).thenReturn(userDtoContent());

        List<ReviewContext> contexts = builder.buildReviewContexts(
                scmService, config, task, List.of(diff), "abc1234");

        assertEquals(1, contexts.size());
        ReviewContext context = contexts.get(0);
        assertNotNull(context.getRelatedClasses());
        assertTrue(context.getRelatedClasses().containsKey("com.acme.common.UserDTO"));
        assertTrue(context.getRelatedClasses().get("com.acme.common.UserDTO").contains("public class UserDTO"));
        assertFalse(context.getRelatedClasses().containsKey("java.util.List"));
        verify(sourceLocationService).locate(
                org.mockito.ArgumentMatchers.argThat(req -> "com.acme.common.UserDTO".equals(req.getQualifiedName())));
    }

    @Test
    @DisplayName("索引未命中时保留旧路径推断兜底")
    void indexMissFallsBackToLegacyImportPathResolution() {
        ScmConfig config = config(99L, "[\"com.example\"]", null, null, 5);
        ReviewTask task = task(null);
        DiffFile diff = diff("order-service/src/main/java/com/example/order/OrderService.java");
        String fullContent = """
                package com.example.order;

                import com.example.common.UserDTO;

                public class OrderService {
                    private UserDTO user;
                }
                """;
        String legacyPath = "order-service/src/main/java/com/example/common/UserDTO.java";

        when(scmService.getFileContent(config, task, diff.getNewPath(), "feature/order")).thenReturn(fullContent);
        when(sourceLocationService.locate(any(SourceLocateReqDTO.class))).thenReturn(notMatched());
        when(scmService.getFileContent(config, task, legacyPath, "feature/order")).thenReturn(userDtoContent());

        List<ReviewContext> contexts = builder.buildReviewContexts(
                scmService, config, task, List.of(diff), "feature/order");

        assertEquals(1, contexts.size());
        assertTrue(contexts.get(0).getRelatedClasses().containsKey("com.example.common.UserDTO"));
        verify(scmService).getFileContent(config, task, legacyPath, "feature/order");
    }

    @Test
    @DisplayName("关联类数量仍受 maxRelatedClasses 控制")
    void relatedClassLimitStillApplies() {
        ScmConfig config = config(99L, null, null, null, 2);
        ReviewTask task = task("abc1234");
        DiffFile diff = diff("app/src/main/java/com/acme/order/OrderService.java");
        String fullContent = """
                package com.acme.order;

                import com.acme.A;
                import com.acme.B;
                import com.acme.C;

                public class OrderService {
                }
                """;

        when(sourceLocationService.locate(any(SourceLocateReqDTO.class))).thenAnswer(invocation -> {
            SourceLocateReqDTO request = invocation.getArgument(0);
            String qualifiedName = request.getQualifiedName();
            return matched(qualifiedName, "src/main/java/" + qualifiedName.replace('.', '/') + ".java", "abc1234");
        });
        when(scmService.getFileContent(eq(config), eq(task), anyString(), eq("abc1234"))).thenAnswer(invocation -> {
            String path = invocation.getArgument(2);
            if (diff.getNewPath().equals(path)) {
                return fullContent;
            }
            String className = path.substring(path.lastIndexOf('/') + 1, path.length() - ".java".length());
            return "package com.acme;\n\npublic class " + className + " {\n}\n";
        });

        List<ReviewContext> contexts = builder.buildReviewContexts(
                scmService, config, task, List.of(diff), "abc1234");

        assertEquals(2, contexts.get(0).getRelatedClasses().size());
        assertTrue(contexts.get(0).getRelatedClasses().containsKey("com.acme.A"));
        assertTrue(contexts.get(0).getRelatedClasses().containsKey("com.acme.B"));
        assertFalse(contexts.get(0).getRelatedClasses().containsKey("com.acme.C"));
        verify(sourceLocationService, never()).locate(
                org.mockito.ArgumentMatchers.argThat(req -> "com.acme.C".equals(req.getQualifiedName())));
    }

    private ScmConfig config(Long id,
                             String basePackages,
                             String moduleSourceRoots,
                             String packageModuleMappings,
                             Integer maxRelatedClasses) {
        ScmConfig config = new ScmConfig();
        config.setId(id);
        config.setBasePackages(basePackages);
        config.setModuleSourceRoots(moduleSourceRoots);
        config.setPackageModuleMappings(packageModuleMappings);
        config.setMaxRelatedClasses(maxRelatedClasses);
        config.setMaxContextTokens(16000);
        return config;
    }

    private ReviewTask task(String commitSha) {
        ReviewTask task = new ReviewTask();
        task.setScmConfigId(99L);
        task.setProjectName("demo");
        task.setScmProvider("gitlab");
        task.setSourceBranch("feature/order");
        task.setLastCommitSha(commitSha);
        task.setAuthorId("u1");
        task.setAuthorName("Alice");
        return task;
    }

    private DiffFile diff(String newPath) {
        DiffFile diff = new DiffFile();
        diff.setNewPath(newPath);
        diff.setDiff("""
                @@ -1,3 +1,4 @@
                 public class OrderService {
                +    private UserDTO user;
                 }
                """);
        return diff;
    }

    private SourceLocateResDTO matched(String qualifiedName, String filePath, String commitSha) {
        SourceLocateResDTO response = new SourceLocateResDTO();
        response.setMatched(true);
        response.setConfidence(CodeIndexConstants.Confidence.HIGH);
        response.setMatchType(CodeIndexConstants.MatchType.QUALIFIED_NAME);
        response.setCommitSha(commitSha);
        response.setQualifiedName(qualifiedName);
        response.setFilePath(filePath);
        return response;
    }

    private SourceLocateResDTO notMatched() {
        SourceLocateResDTO response = new SourceLocateResDTO();
        response.setMatched(false);
        response.setConfidence(CodeIndexConstants.Confidence.NONE);
        response.setMatchType(CodeIndexConstants.MatchType.NONE);
        return response;
    }

    private String userDtoContent() {
        return """
                package com.acme.common;

                public class UserDTO {
                    private Long id;

                    public Long getId() {
                        return id;
                    }
                }
                """;
    }
}
