package com.lnzz.argus.rule.dto.res;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @classname: PromptCatalogCategoryResDTO
 * @author: Fantasy
 * @date: 2026/05/18 11:10
 * @description: Prompt 模板目录分类响应，承载一级分类及其模板组清单。
 */
@Data
public class PromptCatalogCategoryResDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 一级分类编码。
     */
    private String category;

    /**
     * 一级分类名称。
     */
    private String categoryName;

    /**
     * 分类说明。
     */
    private String description;

    /**
     * 模板组数量。
     */
    private Integer templateCount;

    /**
     * 分类下的模板组清单。
     */
    private List<PromptCatalogTemplateItemDTO> templates = new ArrayList<>();

    /**
     * Prompt 模板组条目。
     */
    @Data
    public static class PromptCatalogTemplateItemDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 模板编码。
         */
        private String templateCode;

        /**
         * 模板名称。
         */
        private String templateName;

        /**
         * 模板场景：MAIN / REPAIR / OTHER。
         */
        private String templateScene;

        /**
         * 是否支持仓库级覆盖。
         */
        private Boolean supportScmOverride;

        /**
         * 展示顺序。
         */
        private Integer sortNo;

        /**
         * 模板状态。
         */
        private String status;

        /**
         * 模板描述。
         */
        private String description;
    }
}
