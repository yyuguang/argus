package com.lnzz.argus.review.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 历史编码规范兼容加载器
 * <p>自动扫描 classpath:standards/ 下所有规范文件（支持 .md / .docx / .xlsx / .pptx / .txt），
 * 通过 DocumentParser 统一提取为文本，作为规则管理向量检索未命中时的兼容兜底输入。</p>
 *
 * <p>目录结构：</p>
 * <pre>
 * standards/
 * ├── coding/      编码规范
 * ├── api/         API 规范
 * ├── database/    数据库规范
 * ├── security/    安全规范
 * └── custom/      自定义规范
 * </pre>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodingStandardsLoader {

    private final DocumentParser documentParser;

    /** 支持的文件扩展名 */
    private static final List<String> SUPPORTED_EXTENSIONS = List.of("md", "txt", "docx", "xlsx", "xls", "pptx");

    /** 规范分类及其显示名称 */
    private static final Map<String, String> CATEGORY_NAMES = new LinkedHashMap<>();
    static {
        CATEGORY_NAMES.put("coding", "编码规范");
        CATEGORY_NAMES.put("api", "API 设计规范");
        CATEGORY_NAMES.put("database", "数据库规范");
        CATEGORY_NAMES.put("security", "安全规范");
        CATEGORY_NAMES.put("custom", "自定义规范");
    }

    /** 缓存已加载的规范内容 */
    private volatile String cachedStandards;

    /** 缓存加载的文件列表 */
    private volatile List<String> loadedFiles = new ArrayList<>();

    /**
     * 加载全部编码规范（带缓存）
     */
    public String loadCodingStandards() {
        if (cachedStandards != null) {
            return cachedStandards;
        }
        return doLoad();
    }

    /**
     * 刷新缓存（新增/修改规范文件后调用）
     *
     * @return 重新加载后的文件列表
     */
    public List<String> refreshCache() {
        cachedStandards = null;
        doLoad();
        log.info("编码规范缓存已刷新, loadedFiles={}", loadedFiles);
        return loadedFiles;
    }

    /**
     * 获取已加载的文件列表
     */
    public List<String> getLoadedFiles() {
        return loadedFiles;
    }

    /**
     * 执行加载：扫描 standards/ 下所有分类目录的规范文件
     */
    private synchronized String doLoad() {
        if (cachedStandards != null) {
            return cachedStandards;
        }

        StringBuilder sb = new StringBuilder();
        List<String> files = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        for (Map.Entry<String, String> entry : CATEGORY_NAMES.entrySet()) {
            String category = entry.getKey();
            String displayName = entry.getValue();

            List<Resource> categoryResources = new ArrayList<>();

            // 扫描所有支持的文件格式
            for (String ext : SUPPORTED_EXTENSIONS) {
                String pattern = "classpath:standards/" + category + "/*." + ext;
                try {
                    Resource[] resources = resolver.getResources(pattern);
                    categoryResources.addAll(Arrays.asList(resources));
                } catch (IOException e) {
                    // 目录不存在或为空，跳过
                }
            }

            if (categoryResources.isEmpty()) {
                continue;
            }

            sb.append("\n## ").append(displayName).append("\n\n");

            for (Resource resource : categoryResources) {
                String fileName = resource.getFilename();
                if (fileName == null) {
                    continue;
                }

                try (InputStream is = resource.getInputStream()) {
                    String content = documentParser.parse(is, fileName);

                    sb.append("### ").append(fileName).append("\n\n");
                    sb.append(content).append("\n\n");
                    sb.append("---\n\n");

                    files.add(category + "/" + fileName);
                    log.info("加载规范文件: {}/{}, format={}, length={}",
                            category, fileName, getExtension(fileName), content.length());
                } catch (IOException e) {
                    log.warn("读取规范文件失败: {}/{}", category, fileName, e);
                }
            }
        }

        if (sb.isEmpty()) {
            sb.append("// 未找到编码规范文件，请将规范文件放入 standards/ 目录\n");
            sb.append("// 支持格式: .md, .txt, .docx, .xlsx, .pptx\n");
        }

        this.loadedFiles = files;
        this.cachedStandards = sb.toString();
        log.info("编码规范加载完成: 共 {} 个文件, 总长度 {} 字符, 预估 {} tokens",
                files.size(), cachedStandards.length(), cachedStandards.length() / 4);
        return cachedStandards;
    }

    private String getExtension(String fileName) {
        int dotIdx = fileName.lastIndexOf('.');
        return dotIdx >= 0 ? fileName.substring(dotIdx + 1) : "";
    }
}
