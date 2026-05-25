package com.lnzz.argus.common.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("BasePageRequest - 分页基础请求")
class BasePageRequestTest {

    @Test
    @DisplayName("pageNo 优先于兼容字段 pageIndex")
    void pageNoTakesPrecedenceOverPageIndex() {
        BasePageRequest request = new BasePageRequest();
        request.setPageNo(3);
        request.setPageIndex(2);

        assertEquals(3, request.effectivePageNo());
        assertEquals(3, request.normalizedPageNo());
    }

    @Test
    @DisplayName("空值和非法值使用默认分页")
    void invalidValuesUseDefaultPagination() {
        BasePageRequest request = new BasePageRequest();
        request.setPageNo(0);
        request.setPageSize(-1);

        assertEquals(BasePageRequest.DEFAULT_PAGE_NO, request.normalizedPageNo());
        assertEquals(BasePageRequest.DEFAULT_PAGE_SIZE, request.normalizedPageSize());
    }

    @Test
    @DisplayName("分页大小超过上限时自动裁剪")
    void pageSizeIsCapped() {
        BasePageRequest request = new BasePageRequest();
        request.setPageSize(500);

        assertEquals(BasePageRequest.MAX_PAGE_SIZE, request.normalizedPageSize());
    }
}
