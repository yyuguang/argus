package com.lnzz.argus.review.parser;

import com.lnzz.argus.review.config.ReviewConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContextBuilder - Token 预算分配")
class ContextBuilderTokenTest {

    private ContextBuilder builder;
    private ReviewConfig.TokenConfig tokenConfig;

    @BeforeEach
    void setUp() {
        builder = new ContextBuilder(null);
        tokenConfig = ReviewConfig.defaults().getToken();
    }

    @Test
    @DisplayName("等量 diff 行数 → 均等分配")
    void equalDiffLinesEqualBudget() {
        ReviewConfig.TokenConfig cfg = ReviewConfig.defaults().getToken();
        cfg.setMaxContextTokens(2000);
        cfg.setTemplateReserveTokens(200);
        cfg.setMinTokenPerFile(1);
        // available = 1800, per file = 600 tokens ≈ 2400 chars

        List<ReviewContext> contexts = List.of(
                largeContext("A.java", 10, 3000),
                largeContext("B.java", 10, 3000),
                largeContext("C.java", 10, 3000)
        );

        builder.trimToBudget(contexts, cfg);

        // 每文件预估 token 应接近均分的 600
        for (ReviewContext ctx : contexts) {
            assertTrue(ctx.getEstimatedTokens() > 0);
            assertTrue(ctx.getEstimatedTokens() <= 601, "got " + ctx.getEstimatedTokens());
        }
    }

    @Test
    @DisplayName("diff 行数差异 → 按比例分配")
    void weightedByDiffLines() {
        ReviewConfig.TokenConfig cfg = ReviewConfig.defaults().getToken();
        cfg.setMaxContextTokens(2000);
        cfg.setTemplateReserveTokens(200);
        cfg.setMinTokenPerFile(1);
        // available = 1800, large(diff=100) >> small(diff=10)

        ReviewContext large = largeContext("LargeService.java", 100, 3000);
        ReviewContext small = largeContext("SmallUtil.java", 10, 3000);

        builder.trimToBudget(new ArrayList<>(List.of(large, small)), cfg);

        assertTrue(large.getEstimatedTokens() > small.getEstimatedTokens(),
                "large=" + large.getEstimatedTokens() + " should be > small=" + small.getEstimatedTokens());
    }

    @Test
    @DisplayName("核心模块加权 → 更多预算")
    void coreModuleGetsMoreBudget() {
        ReviewConfig.TokenConfig config = ReviewConfig.defaults().getToken();
        config.setCoreModuleBonus(1.5);
        config.setMaxContextTokens(2000);
        config.setTemplateReserveTokens(200);
        config.setMinTokenPerFile(1);
        // available = 1800, 等 diff 行数但核心模块权重 ×1.5

        // 内容需超过分配的预算线（core~1080, util~720）才会触发截断
        ReviewContext core = largeContext("src/main/java/com/example/service/OrderService.java", 20, 6000);
        ReviewContext util = largeContext("src/main/java/com/example/util/StringUtil.java", 20, 6000);

        builder.trimToBudget(new ArrayList<>(List.of(core, util)), config);

        assertTrue(core.getEstimatedTokens() > util.getEstimatedTokens(),
                "core=" + core.getEstimatedTokens() + " should be > util=" + util.getEstimatedTokens());
    }

    @Test
    @DisplayName("新文件惩罚 → 更少预算")
    void newFileGetsLessBudget() {
        ReviewConfig.TokenConfig config = ReviewConfig.defaults().getToken();
        config.setNewFilePenalty(0.3);
        config.setMaxContextTokens(2000);
        config.setTemplateReserveTokens(200);
        config.setMinTokenPerFile(1);
        // available = 1800, newFile weight = diff * 0.3, existing weight = diff * 1.0

        // 新建文件：diff 与 fullContent 高度一致 → isLikelyNewFile=true
        String largePadding = "    // padding to ensure content exceeds per-file budget for trimming\n".repeat(40);
        String fileContent = "package com.example;\n\npublic class NewService {\n" + largePadding + "}\n";
        ReviewContext newFile = ReviewContext.builder()
                .filePath("NewService.java")
                .languageTag("java")
                .fullContent(fileContent)
                .diffContent("+" + fileContent.replace("\n", "\n+"))
                .addedLineNumbers(List.of(1, 2, 3, 4, 5, 6))
                .build();

        // 既有文件：同 diff 行数但 isLikelyNewFile=false（diff 占比低）
        String existingContent = "package com.example;\n\npublic class ExistingService {\n"
                + largePadding + largePadding + "}\n";  // 2x padding → ratio < 0.7
        ReviewContext existing = ReviewContext.builder()
                .filePath("ExistingService.java")
                .languageTag("java")
                .fullContent(existingContent)
                .diffContent("+    public void doWork() {}\n+    public void doMore() {}\n")
                .addedLineNumbers(List.of(4, 5, 6, 7, 8, 9))
                .build();

        builder.trimToBudget(new ArrayList<>(List.of(newFile, existing)), config);

        assertTrue(newFile.getEstimatedTokens() < existing.getEstimatedTokens(),
                "newFile=" + newFile.getEstimatedTokens() + " should be < existing=" + existing.getEstimatedTokens());
    }

    @Test
    @DisplayName("保底 minTokenPerFile")
    void minTokenPerFileGuaranteed() {
        ReviewConfig.TokenConfig config = ReviewConfig.defaults().getToken();
        config.setMaxContextTokens(400);
        config.setTemplateReserveTokens(200);
        config.setMinTokenPerFile(100);
        config.setNewFilePenalty(0.1); // 极端惩罚
        // available = 200, 1 file → budget = max(100, 200) = 200 tokens ≈ 800 chars

        // 使用大内容确保超过预算线，estimatedTokens 反映预算
        String largeContent = "package com.example;\n\npublic class TinyFile {\n"
                + "    // padding\n".repeat(60) + "}\n";
        ReviewContext ctx = ReviewContext.builder()
                .filePath("TinyFile.java")
                .languageTag("java")
                .fullContent(largeContent)
                .diffContent("+" + largeContent.replace("\n", "\n+"))
                .addedLineNumbers(List.of(1))
                .build();

        builder.trimToBudget(new ArrayList<>(List.of(ctx)), config);

        // minTokenPerFile 保证预算不低于 100
        assertTrue(ctx.getEstimatedTokens() >= 100,
                "got " + ctx.getEstimatedTokens() + ", expected >= 100");
    }

    @Test
    @DisplayName("null / empty contexts 不抛异常")
    void nullOrEmptyContextsSafe() {
        assertDoesNotThrow(() -> builder.trimToBudget(null, tokenConfig));
        assertDoesNotThrow(() -> builder.trimToBudget(List.of(), tokenConfig));
    }

    // ======================== helpers ========================

    /** 构造内容足够大的 context，确保超过单文件预算线，estimatedTokens 反映预算分配 */
    private ReviewContext largeContext(String filePath, int addedLineCount, int contentChars) {
        List<Integer> addedLines = new ArrayList<>();
        for (int i = 0; i < addedLineCount; i++) {
            addedLines.add(i + 1);
        }

        StringBuilder fc = new StringBuilder();
        fc.append("package com.example;\n\npublic class Test {\n");
        int charsPerLine = 70;
        int linesNeeded = Math.max(1, contentChars / charsPerLine);
        for (int i = 0; i < linesNeeded; i++) {
            fc.append("    // padding content to reach token budget: line ").append(i).append("\n");
        }
        fc.append("}\n");

        String diffContent = "+class Test {}\n".repeat(addedLineCount / 2 + 1);

        return ReviewContext.builder()
                .filePath(filePath)
                .languageTag("java")
                .fullContent(fc.toString())
                .diffContent(diffContent)
                .addedLineNumbers(addedLines)
                .build();
    }
}
