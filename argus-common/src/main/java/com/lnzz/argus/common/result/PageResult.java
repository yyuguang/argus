package com.lnzz.argus.common.result;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 统一分页返回结构
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 数据列表 */
    private List<T> records;

    /** 当前页码 */
    private long pageNo;

    /** 每页大小 */
    private long pageSize;

    /** 总记录数 */
    private long total;

    public PageResult() {
    }

    public PageResult(List<T> records, long pageNo, long pageSize, long total) {
        this.records = records;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.total = total;
    }

    public static <T> PageResult<T> of(List<T> records, long pageNo, long pageSize, long total) {
        return new PageResult<>(records, pageNo, pageSize, total);
    }
}
