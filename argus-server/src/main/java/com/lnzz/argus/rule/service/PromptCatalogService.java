package com.lnzz.argus.rule.service;

import com.lnzz.argus.rule.dto.res.PromptCatalogCategoryResDTO;

import java.util.List;

/**
 * @classname: PromptCatalogService
 * @author: Fantasy
 * @date: 2026/05/18 22:02
 * @description: Prompt 模板目录服务接口，负责系统预置 Prompt 分类与模板组目录查询。
 */
public interface PromptCatalogService {

    /**
     * 查询 Prompt 模板目录。
     *
     * @param category 一级分类，可为空
     * @param keyword  模板关键字，可为空
     * @return Prompt 模板分类目录
     * @author Fantasy
     * @date 2026/05/18 22:02
     */
    List<PromptCatalogCategoryResDTO> listCatalog(String category, String keyword);
}
