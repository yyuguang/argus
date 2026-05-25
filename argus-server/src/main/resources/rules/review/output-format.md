
## 输出格式要求

请严格按照以下 JSON 格式输出，不要输出任何其他内容：
- 首字符必须是 `{`，末字符必须是 `}`
- 禁止输出“好的”“收到”“以下是”等任何寒暄或说明
- 禁止使用 Markdown 代码块包裹 JSON

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
