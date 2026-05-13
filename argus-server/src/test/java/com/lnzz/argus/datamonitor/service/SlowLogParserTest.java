package com.lnzz.argus.datamonitor.service;

import com.lnzz.argus.datamonitor.service.SlowLogParser.ParsedSlowLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("SlowLogParser - MySQL 5.7 slow log 解析")
class SlowLogParserTest {

    @Test
    @DisplayName("解析典型 MySQL 5.7 slow log 片段")
    void parseMysql57SlowLogSample() {
        String content = """
                # Time: 260513 10:00:00
                # User@Host: root[root] @ localhost []  Id: 10
                # Query_time: 5.600000  Lock_time: 0.300000 Rows_sent: 20  Rows_examined: 900000
                SET timestamp=1778647200;
                select * from order_main where customer_id = 10001 order by create_time desc limit 1000,20;
                """;

        ParsedSlowLog parsed = new SlowLogParser().parse(content);

        assertEquals(5600L, parsed.queryTimeMs());
        assertEquals(300L, parsed.lockTimeMs());
        assertEquals(20L, parsed.rowsSent());
        assertEquals(900000L, parsed.rowsExamined());
        assertEquals(LocalDateTime.of(2026, 5, 13, 10, 0), parsed.occurredAt());
        assertFalse(parsed.sqlText().endsWith(";"));
    }
}
