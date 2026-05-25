package com.lnzz.argus.rule.dto.res;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @classname: RuleStandardsMigrationResDTO
 * @author: Fantasy
 * @date: 2026/05/17 21:28
 * @description: 历史 standards 目录迁移结果响应，承载导入数量统计与逐文件处理结果。
 */
@Data
public class RuleStandardsMigrationResDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 扫描到的候选文件数量。
     */
    private Integer totalCount = 0;

    /**
     * 成功导入数量。
     */
    private Integer importedCount = 0;

    /**
     * 跳过数量。
     */
    private Integer skippedCount = 0;

    /**
     * 失败数量。
     */
    private Integer failedCount = 0;

    /**
     * 逐文件处理结果。
     */
    private List<ItemDTO> items = new ArrayList<>();

    /**
     * 单文件迁移结果。
     */
    @Data
    public static class ItemDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 规范分类目录。
         */
        private String categoryDir;

        /**
         * 规则管理分类编码。
         */
        private String category;

        /**
         * 文件名。
         */
        private String fileName;

        /**
         * 文档名称。
         */
        private String documentName;

        /**
         * 处理状态：IMPORTED/SKIPPED/FAILED。
         */
        private String status;

        /**
         * 导入后的规则文档 ID。
         */
        private Long documentId;

        /**
         * 处理说明。
         */
        private String message;
    }
}
