package com.lnzz.argus.review.ai;

import com.lnzz.argus.knowledge.vector.VectorKnowledgeService;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.review.entity.ReviewerProfile;
import com.lnzz.argus.review.parser.ReviewContext;
import com.lnzz.argus.review.service.ReviewerProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * M3-B: Prompt 构建器
 * <p>将 ReviewContext + 编码规范注入到结构化 Prompt 中，维度权重和严重度从 ReviewConfig 动态生成。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class PromptBuilder {

    private static final int MAX_STANDARDS_CHARS = 12_000;
    private static final int MAX_RELATED_CLASS_TOTAL_CHARS = 6_000;

    private static final String HARD_RULES = """
            ## 本次评审核心硬规则（优先级最高）

            1. 所有外部接口调用后的返回值，必须做完整判空、状态码校验、必要日志记录。
            2. catch 块不能空实现；吞异常时至少记录关键上下文，并明确是否继续抛出。
            3. 涉及订单号、用户ID、业务主键等关键字段时，必须保证数据链路完整可追踪。
            4. 变更中的 public 方法、核心业务分支、外部系统交互点，必须具备足够的可读性和可维护性。
            5. 会导致生产事故、数据错误、严重排障困难的问题，必须判定为阻塞问题。
            """;

    private static final String REVIEW_CONSTRAINTS = """
            ## 评审约束

            1. 只评审本次 Diff 直接影响的代码；非变更区域只有在直接影响本次改动时才能指出。
            2. 必须优先关注新增/修改行所在的方法、调用链入口、外部接口交互点和异常处理分支。
            3. 不能猜测代码外部事实；如果证据不足，最多给出 SUGGESTION，不得虚构 CRITICAL/MAJOR。
            4. 同一根因只报告一次，优先指出根因，不要重复报告多个表象问题。
            5. 行号尽量落在变更行或其紧邻上下文；禁止输出明显无关的文件路径和行号。
            6. 每个问题必须说明"证据是什么、风险是什么、建议怎么改"。
            """;

    private static final String FEW_SHOT_EXAMPLES = """
            ## Few-shot 示例

            ### 示例 1：应识别为阻塞问题

            变更代码：
            ```java
            Result result = wmsClient.createShipment(dto);
            String orderNo = result.getData().getOrderNo();
            saveOrder(orderNo);
            ```

            正确输出要点：
            - severity: CRITICAL
            - category: DATA_SAFETY
            - description: 外部接口返回值未判空，`result` 或 `result.getData()` 为空时会触发 NPE
            - reasoning: 证据是代码直接解引用外部响应对象，没有任何空值和状态校验
            - isBlocker: true

            ### 示例 2：不要夸大普通问题

            变更代码：
            ```java
            public void handle() {
                int cnt = 0;
                // TODO rename variable
                process(cnt);
            }
            ```

            正确输出要点：
            - 变量命名一般、注释不佳，最多是 MINOR 或 SUGGESTION
            - 不能因为代码风格普通就判定为 CRITICAL/MAJOR
            - 如果没有明确的运行时风险，不得使用阻塞级别
            """;

    private final VectorKnowledgeService vectorKnowledgeService;
    private final ReviewerProfileService reviewerProfileService;

    public PromptBuilder() {
        this(null, null);
    }

    @Autowired
    public PromptBuilder(VectorKnowledgeService vectorKnowledgeService,
                         ReviewerProfileService reviewerProfileService) {
        this.vectorKnowledgeService = vectorKnowledgeService;
        this.reviewerProfileService = reviewerProfileService;
    }

    /**
     * M3-B01: 构建 AI 评审 Prompt
     *
     * @param context 评审上下文
     * @param codingStandards 编码规范内容
     * @param config 仓库级评审配置
     * @return 完整 Prompt
     */
    public String buildReviewPrompt(ReviewContext context, String codingStandards, ReviewConfig config) {
        StringBuilder prompt = new StringBuilder();
        String languageTag = resolveLanguageTag(context);
        String reviewFocus = resolveReviewFocus(context);

        prompt.append(buildSystemInstruction(config));
        prompt.append("\n\n");

        prompt.append(HARD_RULES).append("\n\n");
        prompt.append(REVIEW_CONSTRAINTS).append("\n\n");

        // 注入编码规范
        prompt.append("## 团队规范参考（低于核心硬规则优先级）\n\n");
        prompt.append(trimStandards(codingStandards));
        prompt.append("\n\n");

        // 注入代码文件
        prompt.append("## 待评审文件\n\n");
        prompt.append("文件路径: `").append(context.getFilePath()).append("`\n\n");
        prompt.append("文件类型: `").append(languageTag).append("`\n\n");
        prompt.append("建议重点关注: ").append(reviewFocus).append("\n\n");

        prompt.append("### 完整文件内容\n```").append(languageTag).append("\n");
        prompt.append(context.getFullContent() != null ? context.getFullContent() : "// 无法获取文件内容");
        prompt.append("\n```\n\n");

        prompt.append("### 本次变更 Diff\n```diff\n");
        prompt.append(context.getDiffContent() != null ? context.getDiffContent() : "// 无 Diff 内容");
        prompt.append("\n```\n\n");

        // 注入关联类
        if (context.getRelatedClasses() != null && !context.getRelatedClasses().isEmpty()) {
            prompt.append("### 关联类摘要（供参考调用关系）\n\n");
            int remainingChars = MAX_RELATED_CLASS_TOTAL_CHARS;
            for (Map.Entry<String, String> entry : context.getRelatedClasses().entrySet()) {
                if (remainingChars <= 0) {
                    prompt.append("// ... 更多关联类已省略，以控制上下文大小 ...\n\n");
                    break;
                }
                prompt.append("#### ").append(entry.getKey()).append("\n```java\n");
                String snippet = trimSnippet(entry.getValue(), remainingChars);
                prompt.append(snippet);
                prompt.append("\n```\n\n");
                remainingChars -= snippet.length();
            }
        }

        appendProfileInjection(prompt, context, config);
        appendTeamKnowledgeInjection(prompt, context, config);

        prompt.append(FEW_SHOT_EXAMPLES);
        prompt.append(OUTPUT_FORMAT);

        return prompt.toString();
    }

    // ======================== 动态 System Instruction ========================

    private String buildSystemInstruction(ReviewConfig config) {
        ReviewConfig.DimensionsConfig dims = config.getScoring().getDimensions();
        Map<String, ReviewConfig.SeverityDefConfig> sevDefs = config.getScoring().getSeverityDefinitions();

        StringBuilder sb = new StringBuilder();
        sb.append("""
                你是 Argus 代码评审 AI，一位严格、克制、证据导向的高级工程变更审查员。
                你的职责是对代码、SQL、配置文件等变更执行"可阻止合并"的工程评审，并输出稳定、结构化、可落地的结果。

                ## 评审维度与权重

                """);

        sb.append("1. **规范合规（").append(dims.getCompliance()).append("%）**：")
                .append("检查命名规范、注释、日志、代码风格是否符合团队编码规范\n");
        sb.append("2. **逻辑正确（").append(dims.getCorrectness()).append("%）**：")
                .append("检查空指针风险、边界条件、异常处理、逻辑漏洞、脚本执行顺序问题\n");
        sb.append("3. **数据完整（").append(dims.getDataIntegrity()).append("%）**：")
                .append("检查接口返回值解析、SQL 数据修复风险、配置项是否会破坏数据链路\n");
        sb.append("4. **性能风险（").append(dims.getPerformance()).append("%）**：")
                .append("检查 N+1 查询、大对象拷贝、慢 SQL、循环远程调用、资源泄露\n");
        sb.append("5. **可维护性（").append(dims.getMaintainability()).append("%）**：")
                .append("检查方法长度、嵌套深度、圈复杂度、重复代码、配置可读性与可回滚性\n");

        sb.append("\n## 问题严重度定义\n\n");

        for (Map.Entry<String, ReviewConfig.SeverityDefConfig> entry : sevDefs.entrySet()) {
            String key = entry.getKey();
            ReviewConfig.SeverityDefConfig def = entry.getValue();
            sb.append("- **").append(key).append("（").append(def.getLabel()).append("，扣 ")
                    .append(def.getDeduction()).append(" 分）**：");
            if (def.getExamples() != null && !def.getExamples().isEmpty()) {
                sb.append(String.join("、", def.getExamples()));
            }
            sb.append("\n");
        }

        sb.append("""

                ## 评审要求

                1. 结论必须基于输入代码和上下文中的直接证据，禁止脑补未提供的事实。
                2. 只评审本次 Diff 变更，但要结合完整文件上下文分析变更影响。
                3. 优先审查：外部接口调用、异常处理、判空链路、日志、关键字段传递、SQL 变更风险、配置变更风险。
                4. 每个问题必须指出具体文件路径和尽量准确的行号。
                5. 每个问题必须给出修复建议；CRITICAL/MAJOR 必须给出简短修复方向。
                6. 如果没有发现问题，也要给出亮点和简明总结。
                7. 如果是 SQL 文件，重点检查：DDL/DML 风险、where 条件缺失、索引影响、兼容性、事务与回滚风险。
                8. 如果是 YAML/XML/Properties/JSON 等配置文件，重点检查：环境配置误改、超时/线程池/连接池参数异常、开关项风险、日志级别、路由或权限配置错误。
                """);

        return sb.toString();
    }

    // ======================== 工具方法 ========================

    private String trimStandards(String codingStandards) {
        if (codingStandards == null) {
            return "// 未加载到编码规范";
        }
        if (codingStandards.length() <= MAX_STANDARDS_CHARS) {
            return codingStandards;
        }
        return codingStandards.substring(0, MAX_STANDARDS_CHARS)
                + "\n\n// ... 编码规范内容较长，已按长度截断，保留前置关键部分 ...";
    }

    private String trimSnippet(String content, int maxChars) {
        if (content == null) {
            return "// 关联类内容为空";
        }
        if (content.length() <= maxChars) {
            return content;
        }
        if (maxChars <= 32) {
            return "// 关联类摘要已省略";
        }
        return content.substring(0, maxChars) + "\n// ... 关联类摘要已截断 ...";
    }

    private String resolveLanguageTag(ReviewContext context) {
        if (context.getLanguageTag() == null || context.getLanguageTag().isBlank()) {
            return "text";
        }
        return context.getLanguageTag();
    }

    private String resolveReviewFocus(ReviewContext context) {
        String languageTag = resolveLanguageTag(context);
        return switch (languageTag) {
            case "java" -> "判空链路、异常处理、外部接口调用、关键字段传递、日志和可维护性";
            case "sql" -> "DDL/DML 风险、全表更新删除、索引影响、兼容性、事务与回滚方案";
            case "yaml", "properties", "xml", "json" ->
                    "环境配置误改、开关项风险、连接池/线程池/超时参数、权限和路由配置";
            case "bash", "dockerfile" -> "执行安全、环境依赖、路径/权限、可移植性、失败回滚";
            default -> "变更是否引入逻辑风险、配置风险、可维护性问题或发布风险";
        };
    }

    private void appendProfileInjection(StringBuilder prompt, ReviewContext context, ReviewConfig config) {
        if (!config.getProfile().isInjectEnabled()
                || reviewerProfileService == null
                || vectorKnowledgeService == null
                || context.getAuthorName() == null
                || context.getScmProvider() == null) {
            return;
        }

        ReviewerProfile profile = reviewerProfileService.getProfile(context.getAuthorName(), context.getScmProvider());
        if (profile == null) {
            return;
        }

        String queryText = buildIssueSearchText(context);
        List<Document> similarIssues = vectorKnowledgeService.searchSimilarIssues(
                queryText,
                resolveAuthorId(context),
                context.getProjectName(),
                config.getProfile().getInjectTopk(),
                config.getVector().getMinSimilarity());

        prompt.append("## 提交者画像\n\n");
        prompt.append("- 近 ").append(config.getProfile().getRecentReviewCount())
                .append(" 次评审平均分：")
                .append(resolveAvgScore(profile.getAvgScore())).append("/100\n");

        if (similarIssues.isEmpty()) {
            prompt.append("- 暂无相似历史问题样本\n\n");
            return;
        }

        prompt.append("- 近期相似变更中出现过的问题：\n");
        for (Document doc : similarIssues) {
            prompt.append("  - ").append(formatIssueReference(doc)).append("\n");
        }
        prompt.append("- 请在评审时重点检查以上薄弱点。\n\n");
    }

    private void appendTeamKnowledgeInjection(StringBuilder prompt, ReviewContext context, ReviewConfig config) {
        if (vectorKnowledgeService == null || !config.getVector().isEnabled()) {
            return;
        }

        List<Document> similarIssues = vectorKnowledgeService.searchSimilarIssues(
                buildIssueSearchText(context),
                null,
                context.getProjectName(),
                config.getVector().getReviewSearchTopk(),
                config.getVector().getMinSimilarity());

        if (similarIssues.isEmpty()) {
            return;
        }

        prompt.append("## 历史相似问题参考\n\n");
        for (Document doc : similarIssues) {
            prompt.append("- ").append(formatIssueReference(doc)).append("\n");
        }
        prompt.append("\n");
    }

    private String buildIssueSearchText(ReviewContext context) {
        StringBuilder sb = new StringBuilder();
        if (context.getFilePath() != null) {
            sb.append(context.getFilePath()).append(' ');
        }
        if (context.getDiffContent() != null) {
            sb.append(context.getDiffContent());
        }
        return sb.toString().trim();
    }

    private String formatIssueReference(Document doc) {
        String rule = Objects.toString(doc.getMetadata().get("rule"), "");
        String category = Objects.toString(doc.getMetadata().get("category"), "");
        String text = doc.getText() != null ? doc.getText() : "";
        String compactText = text.length() > 120 ? text.substring(0, 120) + "..." : text;
        if (!rule.isBlank()) {
            return "[" + rule + "] " + compactText;
        }
        if (!category.isBlank()) {
            return "[" + category + "] " + compactText;
        }
        return compactText;
    }

    private String resolveAuthorId(ReviewContext context) {
        if (context.getAuthorId() != null && !context.getAuthorId().isBlank()) {
            return context.getAuthorId();
        }
        return context.getScmProvider() + ":" + context.getAuthorName();
    }

    private String resolveAvgScore(BigDecimal avgScore) {
        if (avgScore == null) {
            return "-";
        }
        return avgScore.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    /** 输出格式要求 */
    private static final String OUTPUT_FORMAT = """

            ## 输出格式要求

            请严格按照以下 JSON 格式输出，不要输出任何其他内容：

            ```json
            {
              "scores": {
                "compliance": 85,
                "correctness": 70,
                "dataSafety": 60,
                "performance": 90,
                "maintainability": 75
              },
              "issues": [
                {
                  "severity": "CRITICAL",
                  "category": "DATA_SAFETY",
                  "filePath": "src/main/java/com/example/Service.java",
                  "startLine": 45,
                  "endLine": 48,
                  "isBlocker": true,
                  "confidence": 0.92,
                  "description": "调用WMS接口后未对返回值判空，可能导致NPE",
                  "reasoning": "代码直接访问 result.getData().getOrderNo()，但前面没有任何 result 或 data 的空值校验。",
                  "suggestion": "添加空值判断和异常处理",
                  "fixPriority": "HIGH",
                  "codeSnippet": "Result result = wmsClient.call(dto);\\nString orderNo = result.getData().getOrderNo();",
                  "rule": "所有外部接口返回值必须判空"
                }
              ],
              "highlights": [
                "使用了统一的业务异常处理",
                "命名规范清晰"
              ],
              "summary": "本次提交整体质量中等，主要问题在于外部接口返回值缺少判空处理，建议补充后重新提交"
            }
            ```
            """;
}
