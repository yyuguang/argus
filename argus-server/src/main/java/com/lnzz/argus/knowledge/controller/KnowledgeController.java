package com.lnzz.argus.knowledge.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.knowledge.entity.KnowledgeAudit;
import com.lnzz.argus.knowledge.entity.KnowledgeEntry;
import com.lnzz.argus.knowledge.entity.KnowledgeEntryStatus;
import com.lnzz.argus.knowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理 Controller（M8）
 * <p>提供知识条目的查询、确认、误报标记、忽略和白名单管理接口</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    // ======================== 查询 ========================

    /**
     * 按状态或错误类型筛选知识条目
     */
    @GetMapping("/entries")
    public Result<List<KnowledgeEntry>> listEntries(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String errorType) {
        if (errorType != null && !errorType.isEmpty()) {
            return Result.success(knowledgeService.listByErrorType(errorType));
        }
        if (status != null && !status.isEmpty()) {
            return Result.success(knowledgeService.listByStatus(status));
        }
        return Result.success(knowledgeService.listByStatus(KnowledgeEntryStatus.CONFIRMED.getCode()));
    }

    /**
     * 查询单条知识条目
     */
    @GetMapping("/entries/{id}")
    public Result<KnowledgeEntry> getEntry(@PathVariable Long id) {
        KnowledgeEntry entry = knowledgeService.getById(id);
        if (entry == null) {
            return Result.fail("知识条目不存在: " + id);
        }
        return Result.success(entry);
    }

    /**
     * 查找白名单候选
     */
    @GetMapping("/whitelist-candidates")
    public Result<List<KnowledgeEntry>> whitelistCandidates(
            @RequestParam(defaultValue = "5") int minOccurrence) {
        return Result.success(knowledgeService.findWhitelistCandidates(minOccurrence));
    }

    // ======================== M8-A04: 操作留痕查询 ========================

    /**
     * 查询条目的操作留痕
     */
    @GetMapping("/entries/{id}/audit")
    public Result<List<KnowledgeAudit>> getAuditLog(@PathVariable Long id) {
        return Result.success(knowledgeService.getAuditLog(id));
    }

    // ======================== M8-A03: 人工操作 ========================

    /**
     * 确认知识条目
     */
    @PostMapping("/entries/{id}/confirm")
    public Result<KnowledgeEntry> confirm(@PathVariable Long id,
                                           @RequestParam String operator,
                                           @RequestParam(required = false) String comment) {
        log.info("人工确认知识条目: id={}, operator={}", id, operator);
        KnowledgeEntry entry = knowledgeService.confirm(id, operator,
                comment != null ? comment : "人工确认");
        return Result.success(entry);
    }

    /**
     * 标记为误报
     */
    @PostMapping("/entries/{id}/false-positive")
    public Result<KnowledgeEntry> markFalsePositive(@PathVariable Long id,
                                                      @RequestParam String operator,
                                                      @RequestParam(required = false) String comment) {
        log.info("标记知识条目为误报: id={}, operator={}", id, operator);
        KnowledgeEntry entry = knowledgeService.markFalsePositive(id, operator,
                comment != null ? comment : "人工判定误报");
        return Result.success(entry);
    }

    /**
     * 忽略/废弃条目
     */
    @PostMapping("/entries/{id}/ignore")
    public Result<KnowledgeEntry> ignore(@PathVariable Long id,
                                          @RequestParam String operator,
                                          @RequestParam(required = false) String comment) {
        log.info("忽略知识条目: id={}, operator={}", id, operator);
        KnowledgeEntry entry = knowledgeService.ignore(id, operator,
                comment != null ? comment : "人工忽略");
        return Result.success(entry);
    }

    // ======================== M8-A05: 白名单 ========================

    /**
     * 提升为白名单
     */
    @PostMapping("/entries/{id}/promote-whitelist")
    public Result<KnowledgeEntry> promoteWhitelist(@PathVariable Long id,
                                                     @RequestParam String operator) {
        log.info("提升知识条目为白名单: id={}, operator={}", id, operator);
        KnowledgeEntry entry = knowledgeService.promoteWhitelist(id, operator);
        return Result.success(entry);
    }

    /**
     * 从白名单降级
     */
    @PostMapping("/entries/{id}/demote-whitelist")
    public Result<KnowledgeEntry> demoteWhitelist(@PathVariable Long id,
                                                    @RequestParam String operator) {
        log.info("降级白名单: id={}, operator={}", id, operator);
        KnowledgeEntry entry = knowledgeService.demoteWhitelist(id, operator);
        return Result.success(entry);
    }
}
