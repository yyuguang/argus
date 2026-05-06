# 编码规范知识库

本目录是 Argus AI 评审引擎的**评分基础**。所有放在此目录下的文件都会被自动加载并注入到 AI 评审 Prompt 中，作为代码评审的评分依据。

## 支持的文件格式

| 格式 | 扩展名 | 说明 |
|------|--------|------|
| Markdown | `.md` | 推荐格式，直接加载 |
| 纯文本 | `.txt` | 直接加载 |
| Word | `.docx` | 提取段落和表格内容 |
| Excel | `.xlsx` / `.xls` | 提取所有 Sheet 的表格数据 |
| PowerPoint | `.pptx` | 提取每页幻灯片的文本 |

## 目录结构

```
standards/
├── coding/         ← 编码规范（命名、注释、日志、异常处理等）
├── api/            ← API 设计规范（RESTful、返回结构、错误码等）
├── database/       ← 数据库规范（表设计、SQL 规范、索引等）
├── security/       ← 安全规范（敏感数据、权限校验等）
├── custom/         ← 自定义规范（项目特有的规范）
└── README.md       ← 本文件
```

## 使用方式

1. 将公司的规范文档放入对应分类目录（支持 md/docx/xlsx/pptx）
2. Argus 启动时自动扫描并加载所有文件
3. 运行时热更新：调用 `POST /api/v1/standards/refresh` 重新加载
4. 查看已加载文件：`GET /api/v1/standards/files`
5. 预览加载内容：`GET /api/v1/standards/preview`

## 示例

```
standards/
├── coding/
│   ├── Java编码规范.docx        ← 公司编码规范 Word 文档
│   ├── 日志规范.md              ← 日志规范
│   └── 异常处理规范.md           ← 异常处理规范
├── api/
│   ├── 接口设计规范.pptx         ← 团队培训 PPT
│   └── API_STYLE.md
├── database/
│   ├── 数据库设计规范.xlsx       ← 字段命名对照表
│   └── DB_STYLE.md
├── security/
│   └── 安全开发规范.docx
└── custom/
    └── 项目特殊约定.md
```

## 注意事项

- 单个文件建议不超过 5000 字（过长会占用过多 AI Token）
- 规范内容越具体、越有代码示例，AI 评审越精准
- Word 文档中的图片不会被提取（仅提取文字和表格）
- Excel 建议使用清晰的表头，方便 AI 理解
