package com.lnzz.argus.review.ai;

import com.lnzz.argus.review.parser.ReviewContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * M3-B: Prompt 构建器
 * <p>将 ReviewContext + 编码规范注入到结构化 Prompt 中</p>
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
            6. 每个问题必须说明“证据是什么、风险是什么、建议怎么改”。
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

    /**
     * M3-B01: 构建 AI 评审 Prompt
     *
     * @param context 评审上下文
     * @param codingStandards 编码规范内容
     * @return 完整 Prompt
     */
    public String buildReviewPrompt(ReviewContext context, String codingStandards) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(SYSTEM_INSTRUCTION);
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

        prompt.append("### 完整文件内容\n```java\n");
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

        prompt.append(FEW_SHOT_EXAMPLES);
        prompt.append(OUTPUT_FORMAT);

        return prompt.toString();
    }

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

    /** 系统指令 */
    private static final String SYSTEM_INSTRUCTION = """
            你是 Argus 代码评审 AI，一位严格、克制、证据导向的高级 Java 代码审查员。
            你的职责是对代码变更执行“可阻止合并”的工程评审，并输出稳定、结构化、可落地的结果。
            
            ## 评审维度与权重
            
            1. **规范合规（30%）**：检查命名规范、注释、日志、代码风格是否符合团队编码规范
            2. **逻辑正确（25%）**：检查空指针风险、边界条件、异常处理、逻辑漏洞
            3. **数据完整（20%）**：检查接口调用返回值是否正确判空和解析、参数传递是否完整
            4. **性能风险（15%）**：检查 N+1 查询、大对象拷贝、循环远程调用、资源泄露
            5. **可维护性（10%）**：检查方法长度、嵌套深度、圈复杂度、重复代码
            
            ## 问题严重度定义
            
            - **CRITICAL（致命，扣 15 分）**：有明确证据表明会导致生产事故、数据错误、NPE、严重排障困难或安全风险，必须修复。
            - **MAJOR（严重，扣 8 分）**：高概率 bug、异常处理不完整、关键日志缺失、关键返回值校验不完整，强烈建议修复。
            - **MINOR（一般，扣 3 分）**：可维护性、命名、注释、局部风格等一般问题，不直接导致事故。
            - **SUGGESTION（建议，不扣分）**：优化建议、可选重构建议、证据不足但值得关注的问题。
            
            ## 评审要求
            
            1. 结论必须基于输入代码和上下文中的直接证据，禁止脑补未提供的事实。
            2. 只评审本次 Diff 变更，但要结合完整文件上下文分析变更影响。
            3. 优先审查：外部接口调用、异常处理、判空链路、日志、关键字段传递。
            4. 每个问题必须指出具体文件路径和尽量准确的行号。
            5. 每个问题必须给出修复建议；CRITICAL/MAJOR 必须给出简短修复方向。
            6. 如果没有发现问题，也要给出亮点和简明总结。
            """;

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
