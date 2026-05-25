package com.lnzz.argus.rule.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.rule.dto.req.PromptTemplateSaveReqDTO;
import com.lnzz.argus.rule.dto.res.PromptCatalogCategoryResDTO;
import com.lnzz.argus.rule.dto.res.PromptTemplateSchemeResDTO;
import com.lnzz.argus.rule.service.PromptCatalogService;
import com.lnzz.argus.rule.service.PromptTemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PromptTemplateController - Prompt 模板接口")
class PromptTemplateControllerTest {

    @Test
    @DisplayName("目录接口直接返回分类目录")
    void getCatalogReturnsCatalogData() {
        PromptCatalogService promptCatalogService = mock(PromptCatalogService.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        PromptTemplateController controller = new PromptTemplateController(promptCatalogService, promptTemplateService);
        PromptCatalogCategoryResDTO category = new PromptCatalogCategoryResDTO();
        category.setCategory("CODE_REVIEW");
        when(promptCatalogService.listCatalog("CODE_REVIEW", "review")).thenReturn(List.of(category));

        Result<List<PromptCatalogCategoryResDTO>> result = controller.getCatalog("CODE_REVIEW", "review");

        assertEquals(0, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("CODE_REVIEW", result.getData().get(0).getCategory());
    }

    @Test
    @DisplayName("保存仓库级方案时透传路径参数和请求体")
    void saveScmSchemeDelegatesToService() {
        PromptCatalogService promptCatalogService = mock(PromptCatalogService.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        PromptTemplateController controller = new PromptTemplateController(promptCatalogService, promptTemplateService);
        PromptTemplateSaveReqDTO requestDTO = new PromptTemplateSaveReqDTO();
        requestDTO.setContentText("new-template");
        PromptTemplateSchemeResDTO response = new PromptTemplateSchemeResDTO();
        response.setTemplateCode("CODE_REVIEW_MAIN");
        when(promptTemplateService.saveScmScheme(eq(12L), eq("CODE_REVIEW_MAIN"), any(PromptTemplateSaveReqDTO.class)))
                .thenReturn(response);

        Result<PromptTemplateSchemeResDTO> result = controller.saveScmScheme(12L, "CODE_REVIEW_MAIN", requestDTO);

        assertEquals("仓库级 Prompt 模板保存成功", result.getMessage());
        assertEquals("CODE_REVIEW_MAIN", result.getData().getTemplateCode());
        verify(promptTemplateService).saveScmScheme(12L, "CODE_REVIEW_MAIN", requestDTO);
    }
}
