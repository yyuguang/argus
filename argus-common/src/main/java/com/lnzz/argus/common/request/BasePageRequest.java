package com.lnzz.argus.common.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页基础请求。
 * <p>
 * 后端分页接口统一使用请求体承载分页参数，标准字段为 {@code pageNo/pageSize}。
 * {@code pageIndex} 仅作为旧接口或前端模板迁移期兼容字段，业务代码应通过
 * {@link #effectivePageNo()} 读取最终页码。
 * </p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
public class BasePageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 默认页码，分页请求未传或传入非法页码时使用第一页。 */
    public static final int DEFAULT_PAGE_NO = 1;

    /** 默认每页大小，分页请求未传或传入非法分页大小时使用 10 条。 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 最大每页大小，用于避免一次查询返回过多数据。 */
    public static final int MAX_PAGE_SIZE = 200;

    /** 当前页码，从 1 开始，符合后端统一 API 规范。 */
    private Integer pageNo;

    /** 当前页码兼容字段，迁移期用于接收旧前端模板的 pageIndex。 */
    private Integer pageIndex;

    /** 每页大小，服务层会统一兜底默认值和最大值。 */
    private Integer pageSize;

    /**
     * 获取最终生效页码。
     *
     * @return 优先返回 pageNo，pageNo 为空时返回兼容字段 pageIndex
     */
    public Integer effectivePageNo() {
        return pageNo != null ? pageNo : pageIndex;
    }

    /**
     * 获取归一化后的页码。
     *
     * @return 最小为 1 的页码
     */
    public int normalizedPageNo() {
        Integer effectivePageNo = effectivePageNo();
        return effectivePageNo == null || effectivePageNo < DEFAULT_PAGE_NO ? DEFAULT_PAGE_NO : effectivePageNo;
    }

    /**
     * 获取归一化后的每页大小。
     *
     * @return 默认 10、最大 200 的每页大小
     */
    public int normalizedPageSize() {
        if (pageSize == null || pageSize < DEFAULT_PAGE_NO) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
