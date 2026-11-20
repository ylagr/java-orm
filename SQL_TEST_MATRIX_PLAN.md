# JavaORM 全方言 SQL 计算 100% 覆盖率矩阵单测计划

> **制定时间**：2026-11-20  
> **核心目标**：针对 JavaORM 构建系统级、全方言（MySQL、PostgreSQL、Oracle、SQLite、H2）的完整 SQL 语法矩阵单测，追求 **100% 全文本精确断言** 与 **100% SQL 计算分支覆盖**。

---

## 一、 核心执行原则与规范

1. **测试代码独立目录**：
   - 全方言矩阵测试代码全部收敛在 `java-orm-core/src/test/java/com/javaorm/core/sqlmatrix/` 目录下。
   - 严禁与现有通用功能测试混杂，专款专用，便于快速运行与生成覆盖率报告。

2. **严禁手写 DDL 建表 SQL（100% CodeFirst）**：
   - 所有测试实体与数据表结构创建，**严禁使用任何手写 `CREATE TABLE` 原生 SQL**。
   - 必须统一通过 JavaORM 自带的 `CodeFirst` / `SchemaGenerator` API（如 `JavaOrm.codeFirst().syncTable(Entity.class)`）自动推导并执行建表，同时验证 CodeFirst 自身的跨方言可靠性。

3. **100% 全文本精确匹配断言（Strict Full-Text Assertions）**：
   - 严禁使用 `contains("FROM")`、`startsWith("SELECT")` 等模糊断言。
   - 针对每一个测试场景，必须对 5 大数据库方言（MySQL, PostgreSQL, Oracle, SQLite, H2）分别断言：
     1. 生成的最终 SQL 字符串（大小写、反引号/双引号标识符、关键字、占位符语法）**100% 全等**。
     2. 提取并绑定的 `List<Object>` 参数列表（类型、值、顺序）**100% 全等**。

4. **严禁隐式 `SELECT *`，全列显式展开（Explicit Column Expansion）**：
   - 生产级 ORM 规范：当调用 `orm.select(Entity.class)` 时，严禁生成 `SELECT *` 或 `t0.*`，必须依据实体元数据将全部物理列逐一显式展开（带表别名前缀与当前方言转义符），避免新增列引发意外、覆盖索引失效及大字段滥带。

5. **SQL 矩阵断言 + 真实查询实体数据双重校验闭环（Dual Assertion Closure）**：
   - 单测不能仅停留在 AST 生成 SQL 字符串的比对；
   - 每一个单测场景在断言 5 大方言 SQL 与参数 100% 精确匹配后，必须在测试数据源上**真实执行该查询**；
   - 对真实查询返回的实体集合、Map 映射、聚合计算值进行**强类型全字段深度断言**（校验条数、每个字段值正确填充、类型转换、去重生效等），确保 AST 生成、JDBC 执行、PreparedStatement 绑定与 EntityHydrator 装配全链路 100% 正确。

6. **纯 CodeFirst 方式初始化测试种子数据**：
   - 严禁手写 `INSERT INTO ...` 原生 SQL，所有测试基准数据必须通过 `orm.insertBatch(...)` 实体批处理进行初始化。

7. **双层覆盖推进顺序**：
   - **第一阶段（优先推进）**：**SQL 计算与渲染覆盖（SQL AST / Segment Tree / Dialect Generator）**，要求达到 100% 的分支覆盖，确保任意复杂查询在所有方言下生成的 SQL 绝对正确。
   - **第二阶段（功能稳定后）**：**框架执行生命周期代码覆盖（Connection、Transaction、Interceptor、Hydrator、Cache、Reflection）**。

---

## 二、 跨方言支持矩阵

| 数据库方言 | 标识符引用符 | 分页语法 | 占位符格式 | 字符串拼接 / 特殊函数 |
| :--- | :---: | :--- | :---: | :--- |
| **MySQL (8.0+)** | `` `name` `` | `LIMIT ?, ?` | `?` | `CONCAT(a, b)` / `IFNULL(a, b)` |
| **PostgreSQL (12+)**| `"name"` | `LIMIT ? OFFSET ?` | `?` | `(a \|\| b)` / `COALESCE(a, b)` |
| **Oracle (11g/12c+)**| `"NAME"` | `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY` | `?` | `(a \|\| b)` / `NVL(a, b)` |
| **SQLite (3+)** | `"name"` | `LIMIT ? OFFSET ?` | `?` | `(a \|\| b)` / `IFNULL(a, b)` |
| **H2 (2.x)** | `"name"` | `LIMIT ? OFFSET ?` | `?` | `CONCAT(a, b)` / `IFNULL(a, b)` |

---

## 三、 十大 SQL 分类板块与详细计划

### Phase 1: SELECT 投影与基础查询 (Projection & Basic Query)
- **1.1 全列投影 (`selectEntity()`, `select("*")`)**：跨 5 大方言表名与列名转义符测试。
- **1.2 显式指定单列/多列投影 (`select(User::getId, User::getUsername)`)**：带实体别名与无别名。
- **1.3 列重命名与别名 (`as("user_name")`, `selectAs(...)`)**：断言各方言 `AS` 关键字与列别名引号。
- **1.4 算术运算与标量表达式**：加减乘除、列与常量运算（如 `price * 0.8 + 10`）。
- **1.5 DISTINCT 去重查询**：单列 DISTINCT 与多列联合 DISTINCT。
- **1.6 标量/聚合投影**：`COUNT(*)`, `COUNT(col)`, `SUM(col)`, `AVG(col)`, `MIN(col)`, `MAX(col)`。

### Phase 2: WHERE 条件分支与操作符全覆盖 (Predicates & Operators)
- **2.1 基础比较操作符**：`eq` (`=`), `ne` (`<>`), `gt` (`>`), `ge` (`>=`), `lt` (`<`), `le` (`<=`)。
- **2.2 空值检查**：`isNull()`, `isNotNull()`。
- **2.3 集合运算**：`in(collection)`, `notIn(collection)`, 空集合防御分支。
- **2.4 范围检查**：`between(val1, val2)`, `notBetween(val1, val2)`。
- **2.5 模糊匹配与正则**：`like(val)`, `notLike(val)`, `likeLeft(val)`, `likeRight(val)`。
- **2.6 复杂嵌套与逻辑分支**：`and(w -> ...)`, `or(w -> ...)`, 深度嵌套括号生成。
- **2.7 动态条件过滤**：`condition == true` 时生效，`condition == false` 时剔除，避免多余 `AND 1=1`。

### Phase 3: JOIN 多表连接与关联查询 (Joins & Associations)
- **3.1 INNER JOIN 内连接**：两表关联与三表链式关联（`t1.id = t2.user_id`）。
- **3.2 LEFT JOIN 左外连接**：包含多条件 ON 语句（`ON t1.id = t2.user_id AND t2.status = 1`）。
- **3.3 RIGHT JOIN 右外连接**：右连接各方言表现。
- **3.4 FULL OUTER JOIN 全外连接**：测试支持方言（PG/Oracle）与不支持方言（MySQL/SQLite）的处理。
- **3.5 自连接 (Self Join)**：同一实体绑定不同表别名（`User u1` JOIN `User u2`）。
- **3.6 跨表投影与 VO 自动对齐**：Flat VO 多表字段投影与字段名防歧义覆盖。

### Phase 4: GROUP BY, HAVING 与聚合分析 (Aggregation & Grouping)
- **4.1 单列与多列 GROUP BY**：普通分组与表达式分组。
- **4.2 HAVING 过滤条件**：结合聚合函数过滤（如 `HAVING count(*) > 5 AND sum(amount) >= 100`）。
- **4.3 聚合+条件综合查询**：WHERE 过滤 -> GROUP BY 分组 -> HAVING 筛选。
- **4.4 排序后聚合**：在各方言下的语义与语法边界测试。

### Phase 5: ORDER BY, LIMIT / OFFSET 与各方言分页 (Ordering & Pagination)
- **5.1 基础排序**：单列/多列 `ASC` / `DESC`。
- **5.2 NULLS FIRST / NULLS LAST**：
  - PostgreSQL / Oracle 原生语法；
  - MySQL / SQLite / H2 语法自适应（CASE WHEN 或 -col 变换）。
- **5.3 跨方言物理分页 (Pagination)**：
  - MySQL 方言：`LIMIT ?, ?`
  - PostgreSQL 方言：`LIMIT ? OFFSET ?`
  - Oracle 方言：`OFFSET ? ROWS FETCH NEXT ? ROWS ONLY`
  - SQLite 方言：`LIMIT ? OFFSET ?`
  - H2 方言：`LIMIT ? OFFSET ?`
- **5.4 边界分页**：第 1 页 (`offset = 0`)，深分页，只查 limit 不带 offset。

### Phase 6: 子查询与集合操作 (Subqueries & Set Operations)
- **6.1 WHERE 子查询**：`col IN (SELECT ...)`，`col = (SELECT ...)`, `EXISTS (SELECT ...)`，`NOT EXISTS (SELECT ...)`。
- **6.2 FROM 子查询（派生表 / Derived Table）**：`FROM (SELECT ...) sub` 与投影。
- **6.3 SELECT 标量子查询**：`SELECT id, (SELECT count(*) FROM ...) as cnt FROM ...`。
- **6.4 集合操作**：`UNION`, `UNION ALL`, `INTERSECT`, `EXCEPT / MINUS` 跨方言支持。

### Phase 7: 高级 SQL 特性 (Advanced SQL Features)
- **7.1 CASE WHEN 表达式**：简单 CASE 与搜索型 CASE WHEN THEN ELSE END。
- **7.2 窗口函数 (Window Functions)**：`ROW_NUMBER()`, `RANK()`, `DENSE_RANK()`, `PARTITION BY`, `ORDER BY`。
- **7.3 通用表表达式 (CTE / WITH 语句)**：`WITH cte AS (SELECT ...) SELECT * FROM cte`。
- **7.4 悲观锁机制 (Pessimistic Locking)**：
  - `FOR UPDATE`
  - `FOR UPDATE NOWAIT` (Oracle / PG / MySQL 8.0)
  - `FOR UPDATE SKIP LOCKED`
  - SQLite 方言降级与无锁处理。

### Phase 8: INSERT 插入操作 (Inserts & Batching)
- **8.1 实体单行插入**：完整字段插入、忽略空字段插入、主键回填策略。
- **8.2 批量插入 (Batch Insert)**：多值列表插入 `VALUES (...), (...)` 各方言语法。
- **8.3 INSERT INTO ... SELECT ...**：从子查询向目标表插入。
- **8.4 冲突更新 (Upsert / Merge)**：
  - MySQL: `ON DUPLICATE KEY UPDATE`
  - PostgreSQL / SQLite: `ON CONFLICT (...) DO UPDATE SET ...`
  - Oracle: `MERGE INTO ... USING ...`

### Phase 9: UPDATE 增量更新与脏检查 (Updates)
- **9.1 全量字段实体更新**。
- **9.2 按需指定列更新 (`set(col, val)`)**。
- **9.3 表达式更新**：`SET stock = stock - 1`, `SET price = price * 1.1`。
- **9.4 脏检查增量更新**：仅更新修改过的脏字段，未修改字段自动剔除。
- **9.5 乐观锁更新**：`SET version = version + 1 WHERE id = ? AND version = ?`。
- **9.6 逻辑删除标记更新**：`SET is_deleted = 1 WHERE ...`。

### Phase 10: DELETE 删除与物理/逻辑清理 (Deletes)
- **10.1 实体物理删除 (`deleteById`, `deleteEntity`)**。
- **10.2 条件物理删除 (`deleteWhere`)**。
- **10.3 防呆拦截 (Block Attack)**：未加 WHERE 条件的安全拦截防御。
- **10.4 逻辑删除 (Logic Delete)**：自动转为 UPDATE 语句与查询透明过滤。

---

## 四、 实施进度追踪表

| 阶段 | 模块内容 | 方言覆盖数 | 100%全文本断言 | CodeFirst建表 | 完成状态 |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **Phase 1** | SELECT 投影与基础查询 | 5 (MySQL/PG/Oracle/SQLite/H2) | [x] | [x] | **已完成 (100%全绿)** |
| **Phase 2** | WHERE 条件分支与操作符 | 5 (MySQL/PG/Oracle/SQLite/H2) | [x] | [x] | **已完成 (100%全绿)** |
| **Phase 3** | JOIN 多表连接与关联查询 | 5 (MySQL/PG/Oracle/SQLite/H2) | [x] | [x] | **已完成 (100%全绿)** |
| **Phase 4** | GROUP BY, HAVING 与聚合 | 5 (MySQL/PG/Oracle/SQLite/H2) | [x] | [x] | **已完成 (100%全绿)** |
| **Phase 5** | ORDER BY, LIMIT 与分页 | 5 (MySQL/PG/Oracle/SQLite/H2) | [x] | [x] | **已完成 (100%全绿)** |
| **Phase 6** | 子查询与集合操作 | 5 (MySQL/PG/Oracle/SQLite/H2) | [ ] | [ ] | 进行中 |
| **Phase 7** | 窗口函数, CASE WHEN, CTE, 悲观锁 | 5 (MySQL/PG/Oracle/SQLite/H2) | [ ] | [ ] | 待开始 |
| **Phase 8** | INSERT, 批量与 Upsert | 5 (MySQL/PG/Oracle/SQLite/H2) | [ ] | [ ] | 待开始 |
| **Phase 9** | UPDATE, 增量更新与脏检查 | 5 (MySQL/PG/Oracle/SQLite/H2) | [ ] | [ ] | 待开始 |
| **Phase 10**| DELETE, 逻辑删除与防全表删除 | 5 (MySQL/PG/Oracle/SQLite/H2) | [ ] | [ ] | 待开始 |
