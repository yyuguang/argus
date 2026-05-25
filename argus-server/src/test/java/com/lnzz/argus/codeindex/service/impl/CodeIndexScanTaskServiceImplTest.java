package com.lnzz.argus.codeindex.service.impl;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.codeindex.dao.entity.CodeIndexScanTask;
import com.lnzz.argus.codeindex.dao.mapper.CodeIndexScanTaskMapper;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.codeindex.support.CodeIndexEnums;
import com.lnzz.argus.codeindex.support.CodeIndexProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CodeIndexScanTaskServiceImpl - 源码索引扫描任务持久化模型")
class CodeIndexScanTaskServiceImplTest {

    @Test
    @DisplayName("扫描任务实体应映射扫描任务表并覆盖进度字段")
    void scanTaskEntityShouldMapTaskTableAndProgressFields() {
        CodeIndexScanTask task = new CodeIndexScanTask();
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 10, 0);

        task.setTaskNo("CI-20260525-0001");
        task.setScmConfigId(1L);
        task.setScmProvider("GITLAB");
        task.setScmProjectId("10086");
        task.setRepoName("argus");
        task.setBranchName("master");
        task.setCommitSha("abc123");
        task.setScanType("FULL");
        task.setTriggerType("MANUAL");
        task.setForceRebuild(false);
        task.setTaskStatus("RUNNING");
        task.setScanStage("JAVA_PARSING");
        task.setProgressPercent(55);
        task.setStageMessage("正在解析 Java 文件");
        task.setLoadedFileCount(120);
        task.setTotalJavaFileCount(100);
        task.setParsedFileCount(55);
        task.setFailedFileCount(1);
        task.setClassCount(300);
        task.setPackageCount(80);
        task.setWarningCount(2);
        task.setResultIndexId(10L);
        task.setReusedIndexId(9L);
        task.setLatestErrorMessage("parse warning");
        task.setRequestedBy("Fantasy");
        task.setReason("SCM 配置页手动刷新");
        task.setStartedAt(now.minusMinutes(1));
        task.setFinishedAt(now);
        task.setLastHeartbeatAt(now);
        task.setIsDeleted(false);
        task.setVersion(1);

        TableName tableName = CodeIndexScanTask.class.getAnnotation(TableName.class);

        assertNotNull(tableName);
        assertEquals("argus_code_index_scan_task", tableName.value());
        assertEquals("CI-20260525-0001", task.getTaskNo());
        assertEquals(1L, task.getScmConfigId());
        assertEquals("GITLAB", task.getScmProvider());
        assertEquals("10086", task.getScmProjectId());
        assertEquals("argus", task.getRepoName());
        assertEquals("master", task.getBranchName());
        assertEquals("abc123", task.getCommitSha());
        assertEquals("FULL", task.getScanType());
        assertEquals("MANUAL", task.getTriggerType());
        assertEquals(false, task.getForceRebuild());
        assertEquals("RUNNING", task.getTaskStatus());
        assertEquals("JAVA_PARSING", task.getScanStage());
        assertEquals(55, task.getProgressPercent());
        assertEquals("正在解析 Java 文件", task.getStageMessage());
        assertEquals(120, task.getLoadedFileCount());
        assertEquals(100, task.getTotalJavaFileCount());
        assertEquals(55, task.getParsedFileCount());
        assertEquals(1, task.getFailedFileCount());
        assertEquals(300, task.getClassCount());
        assertEquals(80, task.getPackageCount());
        assertEquals(2, task.getWarningCount());
        assertEquals(10L, task.getResultIndexId());
        assertEquals(9L, task.getReusedIndexId());
        assertEquals("parse warning", task.getLatestErrorMessage());
        assertEquals("Fantasy", task.getRequestedBy());
        assertEquals("SCM 配置页手动刷新", task.getReason());
        assertEquals(now.minusMinutes(1), task.getStartedAt());
        assertEquals(now, task.getFinishedAt());
        assertEquals(now, task.getLastHeartbeatAt());
        assertEquals(false, task.getIsDeleted());
        assertEquals(1, task.getVersion());
    }

    @Test
    @DisplayName("扫描任务 Mapper 应继承 MyBatis-Plus BaseMapper")
    void scanTaskMapperShouldExtendBaseMapper() {
        assertTrue(BaseMapper.class.isAssignableFrom(CodeIndexScanTaskMapper.class));
    }

    @Test
    @DisplayName("扫描任务状态枚举应覆盖异步任务状态机")
    void scanTaskStatusEnumsShouldMatchDesign() {
        assertEquals("PENDING", CodeIndexEnums.ScanTaskStatus.PENDING.getCode());
        assertEquals("RUNNING", CodeIndexEnums.ScanTaskStatus.RUNNING.getCode());
        assertEquals("SUCCESS", CodeIndexEnums.ScanTaskStatus.SUCCESS.getCode());
        assertEquals("FAILED", CodeIndexEnums.ScanTaskStatus.FAILED.getCode());
        assertEquals("CANCELED", CodeIndexEnums.ScanTaskStatus.CANCELED.getCode());
        assertEquals("REUSED", CodeIndexEnums.ScanTaskStatus.REUSED.getCode());
        assertTrue(CodeIndexEnums.ScanTaskStatus.SUCCESS.terminal());
        assertTrue(CodeIndexEnums.ScanTaskStatus.FAILED.terminal());
        assertTrue(CodeIndexEnums.ScanTaskStatus.CANCELED.terminal());
        assertTrue(CodeIndexEnums.ScanTaskStatus.REUSED.terminal());
    }

    @Test
    @DisplayName("扫描阶段和进度常量应覆盖任务进度展示")
    void scanStageAndProgressConstantsShouldMatchDesign() {
        assertEquals("WAITING", CodeIndexEnums.ScanStage.WAITING.getCode());
        assertEquals("SCM_READING", CodeIndexEnums.ScanStage.SCM_READING.getCode());
        assertEquals("MODULE_SCANNING", CodeIndexEnums.ScanStage.MODULE_SCANNING.getCode());
        assertEquals("SOURCE_ROOT_DISCOVERING", CodeIndexEnums.ScanStage.SOURCE_ROOT_DISCOVERING.getCode());
        assertEquals("JAVA_PARSING", CodeIndexEnums.ScanStage.JAVA_PARSING.getCode());
        assertEquals("INDEX_AGGREGATING", CodeIndexEnums.ScanStage.INDEX_AGGREGATING.getCode());
        assertEquals("INDEX_PERSISTING", CodeIndexEnums.ScanStage.INDEX_PERSISTING.getCode());
        assertEquals("COMPLETED", CodeIndexEnums.ScanStage.COMPLETED.getCode());
        assertEquals("FAILED", CodeIndexEnums.ScanStage.FAILED.getCode());
        assertEquals(30, CodeIndexConstants.ScanTask.JAVA_PARSING_PROGRESS_START);
        assertEquals(80, CodeIndexConstants.ScanTask.JAVA_PARSING_PROGRESS_END);
        assertEquals(500, CodeIndexConstants.ScanTask.DEFAULT_PROGRESS_INTERVAL);
        assertEquals("CI-", CodeIndexConstants.ScanTask.TASK_NO_PREFIX);
    }

    @Test
    @DisplayName("扫描任务触发类型应与管理端异步扫描设计一致")
    void scanTriggerTypeShouldMatchAsyncScanDesign() {
        assertEquals("MANUAL", CodeIndexEnums.ScanTriggerType.MANUAL.getCode());
        assertEquals("WEBHOOK", CodeIndexEnums.ScanTriggerType.WEBHOOK.getCode());
        assertEquals("DEPLOY_CALLBACK", CodeIndexEnums.ScanTriggerType.DEPLOY_CALLBACK.getCode());
        assertEquals("SCHEDULED", CodeIndexEnums.ScanTriggerType.SCHEDULED.getCode());
    }

    @Test
    @DisplayName("源码索引配置应绑定 argus.code-index 并提供保守默认值")
    void codeIndexPropertiesShouldExposeConservativeDefaults() {
        ConfigurationProperties annotation = CodeIndexProperties.class.getAnnotation(ConfigurationProperties.class);
        CodeIndexProperties properties = new CodeIndexProperties();

        assertNotNull(annotation);
        assertEquals("argus.code-index", annotation.prefix());
        assertTrue(properties.isAsyncScanEnabled());
        assertNotNull(properties.getParser());
        assertEquals(false, properties.getParser().isParallelEnabled());
        assertEquals(2, properties.getParser().getParallelism());
        assertEquals(1000, properties.getParser().getQueueSize());
        assertEquals(500, properties.getParser().getProgressInterval());
    }
}
