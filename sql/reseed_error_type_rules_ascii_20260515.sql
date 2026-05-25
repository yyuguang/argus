-- Rebuild all error type recognition seed data with ASCII text to avoid mojibake caused by client charset mismatch.

SET NAMES utf8mb4;

ALTER TABLE argus_error_type_rule COMMENT = 'Error type recognition rule table';
ALTER TABLE argus_error_type_rule
    MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key ID',
    MODIFY rule_name VARCHAR(100) NOT NULL COMMENT 'Rule name',
    MODIFY error_type VARCHAR(50) NOT NULL COMMENT 'Resolved standard error type',
    MODIFY match_field VARCHAR(30) NOT NULL COMMENT 'Match field: ANY/EXCEPTION_CLASS/CLASS_NAME/STACK_TRACE/MESSAGE/HTTP_STATUS',
    MODIFY match_mode VARCHAR(20) NOT NULL COMMENT 'Match mode: EXACT/CONTAINS/REGEX/RANGE',
    MODIFY pattern VARCHAR(500) NOT NULL COMMENT 'Match pattern',
    MODIFY priority INT NOT NULL DEFAULT 100 COMMENT 'Lower value has higher priority',
    MODIFY enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether enabled',
    MODIFY builtin TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether builtin seed rule',
    MODIFY remark VARCHAR(500) DEFAULT NULL COMMENT 'Remark',
    MODIFY create_by VARCHAR(64) DEFAULT NULL COMMENT 'Created by',
    MODIFY create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    MODIFY update_by VARCHAR(64) DEFAULT NULL COMMENT 'Updated by',
    MODIFY update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time';

DELETE FROM argus_error_type_rule;
ALTER TABLE argus_error_type_rule AUTO_INCREMENT = 1;

INSERT INTO argus_error_type_rule
    (rule_name, error_type, match_field, match_mode, pattern, priority, enabled, builtin, remark)
VALUES
    ('Null pointer exception', 'NULL_POINTER', 'EXCEPTION_CLASS', 'EXACT', 'NullPointerException', 10, 1, 1, 'Standard NPE exception class'),
    ('JDK17 null pointer message', 'NULL_POINTER', 'MESSAGE', 'REGEX', 'Cannot invoke.*because.*is null', 30, 1, 1, 'JDK17 enhanced NPE message'),
    ('Class cast exception', 'CLASS_CAST', 'EXCEPTION_CLASS', 'EXACT', 'ClassCastException', 10, 1, 1, 'Class cast exception'),
    ('Index out of bounds exception', 'INDEX_OUT_OF_BOUNDS', 'EXCEPTION_CLASS', 'REGEX', '(IndexOutOfBoundsException|ArrayIndexOutOfBoundsException|StringIndexOutOfBoundsException)', 10, 1, 1, 'Collection, array, or string index out of bounds'),
    ('IO exception', 'IO_EXCEPTION', 'EXCEPTION_CLASS', 'REGEX', '(IOException|FileNotFoundException|EOFException)', 10, 1, 1, 'File and network IO exceptions'),
    ('Timeout exception', 'TIMEOUT', 'EXCEPTION_CLASS', 'REGEX', '(TimeoutException|ReadTimeoutException|ConnectTimeoutException|SocketTimeoutException)', 10, 1, 1, 'Timeout related exceptions'),
    ('Connection refused exception', 'CONNECTION_REFUSED', 'EXCEPTION_CLASS', 'REGEX', '(ConnectException|NoRouteToHostException)', 10, 1, 1, 'Connection refused or network unreachable'),
    ('Database exception', 'SQL_EXCEPTION', 'EXCEPTION_CLASS', 'REGEX', '(SQLException|DataAccessException|DataIntegrityViolationException|DuplicateKeyException|MysqlDataTruncation|BadSqlGrammarException|MyBatisSystemException|PersistenceException)', 10, 1, 1, 'JDBC, Spring DAO, MyBatis, and MySQL exceptions'),
    ('Business exception', 'BIZ_EXCEPTION', 'EXCEPTION_CLASS', 'REGEX', '(BizException|BusinessException|ServiceException)', 10, 1, 1, 'Business exception base classes'),
    ('HTTP request exception', 'HTTP_ERROR', 'EXCEPTION_CLASS', 'REGEX', '(HttpClientErrorException|HttpServerErrorException|FeignException|NoResourceFoundException|ResponseStatusException|HttpMessageNotReadableException|HttpRequestMethodNotSupportedException)', 10, 1, 1, 'HTTP client, server, and Spring Web request exceptions'),
    ('Message queue exception', 'MQ_ERROR', 'EXCEPTION_CLASS', 'REGEX', '(JMSException|AmqpException|KafkaException|RocketMQException)', 10, 1, 1, 'MQ related exceptions'),
    ('Serialization exception', 'SERIALIZATION_ERROR', 'EXCEPTION_CLASS', 'REGEX', '(JsonProcessingException|JsonParseException|JsonMappingException|NotSerializableException|SerializationException)', 10, 1, 1, 'JSON and object serialization exceptions'),
    ('Nginx 502', 'NGINX_502', 'HTTP_STATUS', 'EXACT', '502', 10, 1, 1, 'Bad Gateway'),
    ('Nginx 503', 'NGINX_503', 'HTTP_STATUS', 'EXACT', '503', 10, 1, 1, 'Service Unavailable'),
    ('Nginx 504', 'NGINX_504', 'HTTP_STATUS', 'EXACT', '504', 10, 1, 1, 'Gateway Timeout'),
    ('Nginx 499', 'NGINX_499', 'HTTP_STATUS', 'EXACT', '499', 10, 1, 1, 'Client Closed Request'),
    ('Nginx 5xx', 'NGINX_5XX', 'HTTP_STATUS', 'RANGE', '500-599', 50, 1, 1, 'Other gateway 5xx status'),
    ('Nginx 4xx', 'NGINX_4XX', 'HTTP_STATUS', 'RANGE', '400-499', 60, 1, 1, 'Other gateway 4xx status');
