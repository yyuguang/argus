# 项目编码规范

# 文档信息
- 文档名称：编码规范
- 当前状态：生效中
- 最近更新阶段：初始化
- 最近更新原因：统一AI代码风格

---

## 1. 总体原则

- 可读性优先
- 一致性优先
- 简单优先
- 业务语义优先

---

## 2. 类设计

- 单一职责，一个类只做一件事
- 禁止 Controller + Service 混写
- 工具类必须无状态
- 避免过度抽象

---

## 3. 方法设计

- 单一职责
- 方法长度 ≤ 50 行
- 嵌套 ≤ 3 层
- 参数 > 3 个必须封装对象
- 优先 early return

---

## 4. 命名规范

### 类名
- Controller / Service / ServiceImpl / DTO / VO / BO

### 方法名
- 动宾结构：createOrder、queryUser

### 变量名
- 禁止：a、tmp、data、obj
- 必须表达业务语义

---

## 5. 注释规范

### 必须写 JavaDoc 的场景
- 所有 public 方法

### JavaDoc 必须包含
- 功能说明
- @param
- @return

author 默认为 Fantsy

### 类注释

```java
/**
 * @classname: HyBookingOrderController
 * @author: Fantasy
 * @date: 2025/11/28 14:03
 * @description: 类描述xxxx
 */
@RestController
@Slf4j
@Api(tags = "航运订舱订单管理接口")
@RequestMapping(value = "/hyBookingOrder")
public class HyBookingOrderController {

}
```

### 方法注释

```java
  /**接口描述xxx
   * @param dto:  
   * @return Boolean
   * @author Fantasy
   * @date 2026/3/25 21:29
  */
  Boolean batchShip(HyBookingImportShipDto dto);
```



### 行内注释
用于：
- 复杂逻辑
- 边界处理
- 业务规则说明

禁止：
```java
// 设置name
user.setName(name);
```

------

## 6. 常量与枚举

- 禁止魔法值
- 状态必须使用枚举
- 常量使用大写下划线

------

## 7. Controller 规范

- 不写业务逻辑
- 不直接访问数据库
- 参数必须对象化
- 返回结构统一

------

## 8. Service 规范

- 负责业务逻辑
- 明确事务边界
- 复杂逻辑拆方法

------

## 9. DAO 规范

- 禁止写业务逻辑
- 禁止 select *
- 更新必须带条件

------

## 10. 异常规范

- 禁止直接 RuntimeException
- 必须使用业务异常
- 禁止吞异常

------

## 11. 日志规范

必须包含：

- 业务ID
- 参数
- 异常

禁止：

- 无意义日志

------

## 12. 空值处理

- 所有外部数据必须判空
- 禁止隐式假设

------

## 13. AI输出要求

必须输出：

- 变更模块
- 变更原因
- 影响范围
- 验证方式
- 风险说明

## 14.包名设计

```
com.lnzz.*
```





