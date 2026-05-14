package com.lnzz.argus.scm.service;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SCM 平台服务工厂
 *
 * @author lnzz
 * @since 1.0.0
 */
@Component
public class ScmPlatformServiceFactory {

    private final Map<String, ScmPlatformService> serviceMap;

    public ScmPlatformServiceFactory(List<ScmPlatformService> services) {
        this.serviceMap = services.stream()
                .collect(Collectors.toMap(ScmPlatformService::getProvider, Function.identity()));
    }

    public ScmPlatformService getRequired(String provider) {
        ScmPlatformService service = serviceMap.get(normalize(provider));
        if (service == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "暂不支持的 SCM 平台: " + provider);
        }
        return service;
    }

    private String normalize(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase();
    }
}
