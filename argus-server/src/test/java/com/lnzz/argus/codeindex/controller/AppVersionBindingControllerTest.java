package com.lnzz.argus.codeindex.controller;

import com.lnzz.argus.codeindex.dto.req.AppVersionBindingReqDTO;
import com.lnzz.argus.codeindex.dto.res.AppVersionBindingResDTO;
import com.lnzz.argus.codeindex.service.AppVersionBindingService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AppVersionBindingController - 应用版本源码绑定接口")
class AppVersionBindingControllerTest {

    @Test
    @DisplayName("绑定接口默认写入 MANUAL 来源并返回当前绑定")
    void bindShouldDefaultSourceAndReturnBinding() {
        AppVersionBindingService service = mock(AppVersionBindingService.class);
        AppVersionBindingController controller = new AppVersionBindingController(service);
        AppVersionBindingReqDTO requestDTO = request();
        AppVersionBindingResDTO responseDTO = response(10L);
        when(service.bind(requestDTO)).thenReturn(responseDTO);

        Result<AppVersionBindingResDTO> result = controller.bind(requestDTO);

        assertEquals("应用源码版本绑定成功", result.getMessage());
        assertEquals(10L, result.getData().getBindingId());
        ArgumentCaptor<AppVersionBindingReqDTO> captor = ArgumentCaptor.forClass(AppVersionBindingReqDTO.class);
        verify(service).bind(captor.capture());
        assertEquals(CodeIndexConstants.TriggerType.MANUAL, captor.getValue().getBindingSource());
    }

    @Test
    @DisplayName("查询当前绑定接口透传 app/environment/scmConfigId")
    void getCurrentBindingShouldDelegateParams() {
        AppVersionBindingService service = mock(AppVersionBindingService.class);
        AppVersionBindingController controller = new AppVersionBindingController(service);
        AppVersionBindingResDTO responseDTO = response(10L);
        when(service.getActiveBinding("order-service", "prod", 1L)).thenReturn(responseDTO);

        Result<AppVersionBindingResDTO> result = controller.getCurrentBinding("order-service", "prod", 1L);

        assertEquals(0, result.getCode());
        assertEquals("order-service", result.getData().getAppName());
        verify(service).getActiveBinding("order-service", "prod", 1L);
    }

    @Test
    @DisplayName("绑定请求缺少 commitSha 时抛出参数错误")
    void bindShouldRejectMissingCommitSha() {
        AppVersionBindingController controller = new AppVersionBindingController(mock(AppVersionBindingService.class));
        AppVersionBindingReqDTO requestDTO = request();
        requestDTO.setCommitSha("");

        BizException exception = assertThrows(BizException.class, () -> controller.bind(requestDTO));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
        assertEquals("commitSha 不能为空", exception.getMessage());
    }

    @Test
    @DisplayName("查询当前绑定缺少参数时抛出参数错误")
    void getCurrentBindingShouldRejectMissingParams() {
        AppVersionBindingController controller = new AppVersionBindingController(mock(AppVersionBindingService.class));

        BizException exception = assertThrows(BizException.class,
                () -> controller.getCurrentBinding("", "prod", 1L));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    private AppVersionBindingReqDTO request() {
        AppVersionBindingReqDTO requestDTO = new AppVersionBindingReqDTO();
        requestDTO.setMappingId(20L);
        requestDTO.setAppName("order-service");
        requestDTO.setEnvironment("prod");
        requestDTO.setScmConfigId(1L);
        requestDTO.setBranchName("main");
        requestDTO.setCommitSha("abc123");
        requestDTO.setVersionName("v1.0.0");
        return requestDTO;
    }

    private AppVersionBindingResDTO response(Long bindingId) {
        AppVersionBindingResDTO responseDTO = new AppVersionBindingResDTO();
        responseDTO.setBindingId(bindingId);
        responseDTO.setAppName("order-service");
        responseDTO.setEnvironment("prod");
        responseDTO.setScmConfigId(1L);
        responseDTO.setCommitSha("abc123");
        responseDTO.setBindingSource(CodeIndexConstants.TriggerType.MANUAL);
        responseDTO.setActive(true);
        return responseDTO;
    }
}
