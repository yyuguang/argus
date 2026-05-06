# 数据库设计规范

# 文档信息

- 文档名称：数据库规范
- 当前状态：生效中
- 最近更新阶段：初始化

## 1. 命名规范

- 表名：snake_case
- 字段名：snake_case
- 禁止拼音缩写

---

## 2. 必备字段

所有业务表必须包含：

- id bigint 主键
- create_by varchar2(64)
- create_time datetime
- update_by varchar2(64)
- update_time datetime

推荐扩展：

- is_deleted
- version

---

## 3. 字段设计

### 金额
- 使用 decimal
- 禁止 float/double

### 状态
- 字段名：status
- 使用枚举

### 标识
- user_id
- order_id
- tenant_id

---

## 4. 禁止字段命名

- field1
- value1
- ext1
- remark2

---

## 5. 字段注释

必须包含：
- 业务含义
- 枚举说明（如有）

---

## 6. 索引规范

必须考虑：

- 查询条件字段加索引
- 唯一约束必须落库
- 联合索引顺序合理

---

## 7. SQL规范

- 禁止 select *
- 必须带 where 条件
- 防止全表更新
- 分页必须排序

---

## 8. 并发设计

必须考虑：

- 幂等性
- 乐观锁 / version
- 唯一约束

---

## 9. 时间规范

- 统一 datetime / timestamp
- 必须明确时区

---

## 10. AI输出要求

生成表结构时必须包含：

- 建表 SQL
- 字段说明: 必须中文
- 索引说明
- 设计理由