package com.lnzz.argus.codeindex.service;

import com.lnzz.argus.codeindex.dto.req.SourceLocateReqDTO;
import com.lnzz.argus.codeindex.dto.res.SourceLocateResDTO;

/**
 * @classname: SourceLocationService
 * @author: Fantasy
 * @date: 2026/05/19 17:05
 * @description: 源码定位服务接口，基于应用版本绑定、提交号、全限定类名或文件路径定位源码。
 */
public interface SourceLocationService {

    /**
     * 定位源码文件。
     *
     * @param requestDTO 定位请求
     * @return 源码定位结果
     */
    SourceLocateResDTO locate(SourceLocateReqDTO requestDTO);
}
