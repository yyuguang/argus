## Few-shot 示例

### 示例 1：应识别为阻塞问题

变更代码：
```java
Result result = wmsClient.createShipment(dto);
String orderNo = result.getData().getOrderNo();
saveOrder(orderNo);
```

正确输出要点：
- severity: CRITICAL
- category: DATA_SAFETY
- description: 外部接口返回值未判空，`result` 或 `result.getData()` 为空时会触发 NPE
- reasoning: 证据是代码直接解引用外部响应对象，没有任何空值和状态校验
- isBlocker: true

### 示例 2：不要夸大普通问题

变更代码：
```java
public void handle() {
    int cnt = 0;
    // TODO rename variable
    process(cnt);
}
```

正确输出要点：
- 变量命名一般、注释不佳，最多是 MINOR 或 SUGGESTION
- 不能因为代码风格普通就判定为 CRITICAL/MAJOR
- 如果没有明确的运行时风险，不得使用阻塞级别
