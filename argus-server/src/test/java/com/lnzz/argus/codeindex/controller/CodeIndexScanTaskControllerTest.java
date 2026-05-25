package com.lnzz.argus.codeindex.controller;

import com.lnzz.argus.codeindex.dto.req.CodeIndexScanTaskCreateReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexScanTaskResDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("CodeIndexScanTaskController - 源码索引扫描任务接口")
class CodeIndexScanTaskControllerTest {

    @Test
    @DisplayName("创建扫描任务请求 DTO 应覆盖异步扫描入参")
    void createTaskRequestShouldExposeAsyncScanFields() {
        CodeIndexScanTaskCreateReqDTO requestDTO = new CodeIndexScanTaskCreateReqDTO();

        requestDTO.setBranchName("master");
        requestDTO.setCommitSha("abc123");
        requestDTO.setScanType("FULL");
        requestDTO.setForceRebuild(false);
        requestDTO.setReason("SCM 配置页手动刷新");

        assertEquals("master", requestDTO.getBranchName());
        assertEquals("abc123", requestDTO.getCommitSha());
        assertEquals("FULL", requestDTO.getScanType());
        assertEquals(false, requestDTO.getForceRebuild());
        assertEquals("SCM 配置页手动刷新", requestDTO.getReason());
    }

    @Test
    @DisplayName("扫描任务响应 DTO 应覆盖阶段进度和结果索引字段")
    void taskResponseShouldExposeProgressAndResultFields() {
        CodeIndexScanTaskResDTO response = new CodeIndexScanTaskResDTO();
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 10, 30);

        response.setTaskId(1001L);
        response.setTaskNo("CI-20260525-1001");
        response.setTaskStatus("RUNNING");
        response.setScanStage("JAVA_PARSING");
        response.setProgressPercent(66);
        response.setStageMessage("正在解析 Java 文件");
        response.setLoadedFileCount(200);
        response.setTotalJavaFileCount(180);
        response.setParsedFileCount(120);
        response.setFailedFileCount(2);
        response.setClassCount(400);
        response.setPackageCount(90);
        response.setWarningCount(3);
        response.setResultIndexId(10L);
        response.setReusedIndexId(9L);
        response.setLatestErrorMessage("parse warning");
        response.setStartedAt(now.minusMinutes(3));
        response.setFinishedAt(now);
        response.setLastHeartbeatAt(now.minusSeconds(10));

        assertEquals(1001L, response.getTaskId());
        assertEquals("CI-20260525-1001", response.getTaskNo());
        assertEquals("RUNNING", response.getTaskStatus());
        assertEquals("JAVA_PARSING", response.getScanStage());
        assertEquals(66, response.getProgressPercent());
        assertEquals("正在解析 Java 文件", response.getStageMessage());
        assertEquals(200, response.getLoadedFileCount());
        assertEquals(180, response.getTotalJavaFileCount());
        assertEquals(120, response.getParsedFileCount());
        assertEquals(2, response.getFailedFileCount());
        assertEquals(400, response.getClassCount());
        assertEquals(90, response.getPackageCount());
        assertEquals(3, response.getWarningCount());
        assertEquals(10L, response.getResultIndexId());
        assertEquals(9L, response.getReusedIndexId());
        assertEquals("parse warning", response.getLatestErrorMessage());
        assertEquals(now.minusMinutes(3), response.getStartedAt());
        assertEquals(now, response.getFinishedAt());
        assertEquals(now.minusSeconds(10), response.getLastHeartbeatAt());
    }
}
