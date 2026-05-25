package com.lnzz.argus.codeindex.controller;

import com.lnzz.argus.codeindex.dto.req.SourceLocateReqDTO;
import com.lnzz.argus.codeindex.dto.res.SourceLocateResDTO;
import com.lnzz.argus.codeindex.service.SourceLocationService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SourceLocationController - 源码定位接口")
class SourceLocationControllerTest {

    @Test
    @DisplayName("定位命中时返回源码位置")
    void locateShouldReturnMatchedLocation() {
        SourceLocationService service = mock(SourceLocationService.class);
        SourceLocationController controller = new SourceLocationController(service);
        SourceLocateReqDTO requestDTO = request();
        SourceLocateResDTO responseDTO = response(true);
        when(service.locate(requestDTO)).thenReturn(responseDTO);

        Result<SourceLocateResDTO> result = controller.locate(requestDTO);

        assertEquals(0, result.getCode());
        assertTrue(result.getData().getMatched());
        assertEquals("src/main/java/com/example/DemoService.java", result.getData().getFilePath());
        verify(service).locate(requestDTO);
    }

    @Test
    @DisplayName("定位未命中时仍返回统一成功结构和未命中数据")
    void locateShouldReturnNotMatchedResult() {
        SourceLocationService service = mock(SourceLocationService.class);
        SourceLocationController controller = new SourceLocationController(service);
        SourceLocateReqDTO requestDTO = request();
        SourceLocateResDTO responseDTO = response(false);
        when(service.locate(requestDTO)).thenReturn(responseDTO);

        Result<SourceLocateResDTO> result = controller.locate(requestDTO);

        assertEquals(0, result.getCode());
        assertFalse(result.getData().getMatched());
        assertEquals(CodeIndexConstants.MatchType.NONE, result.getData().getMatchType());
    }

    @Test
    @DisplayName("定位请求为空时抛出参数错误")
    void locateShouldRejectNullRequest() {
        SourceLocationController controller = new SourceLocationController(mock(SourceLocationService.class));

        BizException exception = assertThrows(BizException.class, () -> controller.locate(null));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    private SourceLocateReqDTO request() {
        SourceLocateReqDTO requestDTO = new SourceLocateReqDTO();
        requestDTO.setScmConfigId(1L);
        requestDTO.setCommitSha("abc123");
        requestDTO.setQualifiedName("com.example.DemoService");
        return requestDTO;
    }

    private SourceLocateResDTO response(boolean matched) {
        SourceLocateResDTO responseDTO = new SourceLocateResDTO();
        responseDTO.setMatched(matched);
        responseDTO.setConfidence(matched ? CodeIndexConstants.Confidence.HIGH : CodeIndexConstants.Confidence.NONE);
        responseDTO.setMatchType(matched ? CodeIndexConstants.MatchType.QUALIFIED_NAME : CodeIndexConstants.MatchType.NONE);
        responseDTO.setFilePath(matched ? "src/main/java/com/example/DemoService.java" : null);
        return responseDTO;
    }
}
