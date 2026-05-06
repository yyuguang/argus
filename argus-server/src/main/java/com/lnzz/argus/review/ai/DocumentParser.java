package com.lnzz.argus.review.ai;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Office 文档解析器
 * <p>将 Word(.docx) / Excel(.xlsx) / PPT(.pptx) 文档提取为纯文本，
 * 供 AI 评审引擎作为编码规范的评分基准</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class DocumentParser {

    /**
     * 根据文件扩展名自动选择解析方式
     *
     * @param inputStream 文件流
     * @param fileName    文件名
     * @return 提取的纯文本
     */
    public String parse(InputStream inputStream, String fileName) throws IOException {
        String ext = getExtension(fileName).toLowerCase();
        return switch (ext) {
            case "md", "txt" -> parseText(inputStream);
            case "docx" -> parseWord(inputStream);
            case "xlsx", "xls" -> parseExcel(inputStream);
            case "pptx" -> parsePpt(inputStream);
            default -> {
                log.warn("不支持的文件格式: {}", fileName);
                yield "// 不支持的文件格式: " + fileName;
            }
        };
    }

    /**
     * 解析纯文本 / Markdown
     */
    private String parseText(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * 解析 Word 文档（.docx）
     * <p>提取段落文本 + 表格内容</p>
     */
    private String parseWord(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(is)) {
            // 提取段落
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText().trim();
                if (!text.isEmpty()) {
                    // 根据段落样式添加 Markdown 标题
                    String style = para.getStyle();
                    if (style != null && style.startsWith("Heading")) {
                        sb.append("## ");
                    }
                    sb.append(text).append("\n");
                }
            }

            // 提取表格
            for (XWPFTable table : doc.getTables()) {
                sb.append("\n");
                for (XWPFTableRow row : table.getRows()) {
                    sb.append("| ");
                    for (XWPFTableCell cell : row.getTableCells()) {
                        sb.append(cell.getText().trim()).append(" | ");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 解析 Excel 文档（.xlsx）
     * <p>提取所有 Sheet 的内容，转为表格文本</p>
     */
    private String parseExcel(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(is)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                sb.append("## Sheet: ").append(sheet.getSheetName()).append("\n\n");

                for (Row row : sheet) {
                    sb.append("| ");
                    for (Cell cell : row) {
                        sb.append(getCellValue(cell)).append(" | ");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 解析 PPT 文档（.pptx）
     * <p>提取每页幻灯片的文本内容</p>
     */
    private String parsePpt(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XMLSlideShow ppt = new XMLSlideShow(is)) {
            int slideNum = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                sb.append("## 第 ").append(slideNum++).append(" 页\n\n");
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText().trim();
                        if (!text.isEmpty()) {
                            sb.append(text).append("\n");
                        }
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 获取 Excel 单元格的文本值
     */
    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private String getExtension(String fileName) {
        int dotIdx = fileName.lastIndexOf('.');
        return dotIdx >= 0 ? fileName.substring(dotIdx + 1) : "";
    }
}
