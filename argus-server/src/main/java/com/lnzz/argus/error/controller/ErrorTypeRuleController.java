package com.lnzz.argus.error.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.error.entity.ErrorTypeRule;
import com.lnzz.argus.error.service.ErrorTypeRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 错误类型识别规则管理 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/error-type-rules")
@RequiredArgsConstructor
public class ErrorTypeRuleController {

    private final ErrorTypeRuleService errorTypeRuleService;

    @GetMapping
    public Result<List<ErrorTypeRule>> list(@RequestParam(required = false) String errorType,
                                            @RequestParam(required = false) Boolean enabled,
                                            @RequestParam(required = false) String keyword) {
        return Result.success(errorTypeRuleService.list(errorType, enabled, keyword));
    }

    @GetMapping("/types")
    public Result<List<Map<String, String>>> types() {
        return Result.success(errorTypeRuleService.listTypes());
    }

    @PostMapping
    public Result<ErrorTypeRule> create(@RequestBody ErrorTypeRule request) {
        return Result.success("错误类型规则创建成功", errorTypeRuleService.create(request));
    }

    @PutMapping("/{id}")
    public Result<ErrorTypeRule> update(@PathVariable Long id,
                                        @RequestBody ErrorTypeRule request) {
        return Result.success("错误类型规则更新成功", errorTypeRuleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        errorTypeRuleService.delete(id);
        return Result.success("错误类型规则删除成功", Map.of("id", id));
    }
}
