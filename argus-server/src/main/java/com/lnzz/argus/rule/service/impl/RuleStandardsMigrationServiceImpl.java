package com.lnzz.argus.rule.service.impl;

import com.lnzz.argus.rule.dao.entity.RuleDocument;
import com.lnzz.argus.rule.dao.mapper.RuleDocumentMapper;
import com.lnzz.argus.rule.dto.req.RuleDocumentImportReqDTO;
import com.lnzz.argus.rule.dto.res.RuleDocumentDetailResDTO;
import com.lnzz.argus.rule.dto.res.RuleStandardsMigrationResDTO;
import com.lnzz.argus.rule.service.RuleDocumentImportService;
import com.lnzz.argus.rule.service.RuleStandardsMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @classname: RuleStandardsMigrationServiceImpl
 * @author: Fantasy
 * @date: 2026/05/17 21:30
 * @description: 历史 standards 目录迁移服务实现，复用规则文档导入链完成一次性迁移。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleStandardsMigrationServiceImpl implements RuleStandardsMigrationService {

    private static final String SOURCE_TYPE_MIGRATION = "MIGRATION";

    private static final Map<String, String> CATEGORY_MAPPING = new LinkedHashMap<>();

    static {
        CATEGORY_MAPPING.put("coding", "CODING");
        CATEGORY_MAPPING.put("api", "API");
        CATEGORY_MAPPING.put("database", "DATABASE");
        CATEGORY_MAPPING.put("security", "SECURITY");
        CATEGORY_MAPPING.put("custom", "CUSTOM");
    }

    private final ResourcePatternResolver resourcePatternResolver;
    private final RuleDocumentMapper ruleDocumentMapper;
    private final RuleDocumentImportService ruleDocumentImportService;

    /**
     * 执行历史 standards 目录迁移。
     *
     * @param activeAfterImport 导入后是否立即启用
     * @return 迁移结果
     */
    @Override
    public RuleStandardsMigrationResDTO migrateHistoricalStandards(Boolean activeAfterImport) {
        RuleStandardsMigrationResDTO response = new RuleStandardsMigrationResDTO();
        List<StandardResourceRef> resources = scanStandardResources();
        response.setTotalCount(resources.size());
        log.info("历史 standards 迁移开始, totalCount={}, activeAfterImport={}",
                resources.size(), Boolean.TRUE.equals(activeAfterImport));
        for (StandardResourceRef resourceRef : resources) {
            RuleStandardsMigrationResDTO.ItemDTO item = migrateSingleResource(resourceRef, Boolean.TRUE.equals(activeAfterImport));
            response.getItems().add(item);
            switch (item.getStatus()) {
                case "IMPORTED" -> response.setImportedCount(response.getImportedCount() + 1);
                case "SKIPPED" -> response.setSkippedCount(response.getSkippedCount() + 1);
                default -> response.setFailedCount(response.getFailedCount() + 1);
            }
        }
        log.info("历史 standards 迁移完成, totalCount={}, importedCount={}, skippedCount={}, failedCount={}",
                response.getTotalCount(), response.getImportedCount(), response.getSkippedCount(), response.getFailedCount());
        return response;
    }

    private List<StandardResourceRef> scanStandardResources() {
        List<StandardResourceRef> resources = new ArrayList<>();
        CATEGORY_MAPPING.forEach((categoryDir, categoryCode) -> resources.addAll(scanCategoryResources(categoryDir, categoryCode)));
        resources.sort(Comparator.comparing(StandardResourceRef::categoryDir).thenComparing(StandardResourceRef::fileName));
        return resources;
    }

    private List<StandardResourceRef> scanCategoryResources(String categoryDir, String categoryCode) {
        List<StandardResourceRef> resources = new ArrayList<>();
        try {
            Resource[] matched = resourcePatternResolver.getResources("classpath:standards/" + categoryDir + "/*.*");
            for (Resource resource : matched) {
                String fileName = resource.getFilename();
                if (!StringUtils.hasText(fileName) || !isSupportedExtension(fileName)) {
                    continue;
                }
                resources.add(new StandardResourceRef(categoryDir, categoryCode, fileName, resource));
            }
        } catch (IOException ex) {
            log.warn("扫描历史 standards 分类目录失败, categoryDir={}", categoryDir, ex);
        }
        return resources;
    }

    private RuleStandardsMigrationResDTO.ItemDTO migrateSingleResource(StandardResourceRef resourceRef,
                                                                       boolean activeAfterImport) {
        RuleStandardsMigrationResDTO.ItemDTO item = new RuleStandardsMigrationResDTO.ItemDTO();
        item.setCategoryDir(resourceRef.categoryDir());
        item.setCategory(resourceRef.categoryCode());
        item.setFileName(resourceRef.fileName());
        item.setDocumentName(resolveDocumentName(resourceRef.fileName()));
        RuleDocument existing = ruleDocumentMapper.selectBySourceTypeAndFileName(
                SOURCE_TYPE_MIGRATION,
                resourceRef.categoryCode(),
                resourceRef.fileName());
        if (existing != null) {
            item.setStatus("SKIPPED");
            item.setDocumentId(existing.getId());
            item.setMessage("已存在同名迁移文档，跳过重复导入");
            return item;
        }
        try (InputStream inputStream = resourceRef.resource().getInputStream()) {
            RuleDocumentImportReqDTO requestDTO = new RuleDocumentImportReqDTO();
            requestDTO.setCategory(resourceRef.categoryCode());
            requestDTO.setScope("GLOBAL");
            requestDTO.setDocumentName(item.getDocumentName());
            requestDTO.setRemark("历史迁移来源: standards/" + resourceRef.categoryDir() + "/" + resourceRef.fileName());
            requestDTO.setActiveAfterImport(activeAfterImport);
            requestDTO.setSourceType(SOURCE_TYPE_MIGRATION);
            RuleDocumentDetailResDTO imported = ruleDocumentImportService.importDocument(
                    inputStream.readAllBytes(),
                    resourceRef.fileName(),
                    requestDTO);
            item.setStatus("IMPORTED");
            item.setDocumentId(imported.getId());
            item.setMessage("导入成功");
            return item;
        } catch (Exception ex) {
            log.error("历史 standards 导入失败, categoryDir={}, fileName={}",
                    resourceRef.categoryDir(), resourceRef.fileName(), ex);
            item.setStatus("FAILED");
            item.setMessage(ex.getMessage());
            return item;
        }
    }

    private boolean isSupportedExtension(String fileName) {
        String ext = resolveFileExt(fileName);
        return switch (ext) {
            case "md", "txt", "docx", "xlsx", "xls", "pptx" -> true;
            default -> false;
        };
    }

    private String resolveDocumentName(String fileName) {
        String ext = resolveFileExt(fileName);
        if (!StringUtils.hasText(ext)) {
            return fileName;
        }
        return fileName.substring(0, fileName.length() - ext.length() - 1);
    }

    private String resolveFileExt(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * standards 分类资源引用。
     *
     * @param categoryDir  历史分类目录
     * @param categoryCode 规则管理分类编码
     * @param fileName     文件名
     * @param resource     资源对象
     */
    private record StandardResourceRef(String categoryDir, String categoryCode, String fileName, Resource resource) {
    }
}
