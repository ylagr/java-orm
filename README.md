# Java-ORM：现代无 APT 字段感知与级联流式 ORM

[![Java 8+](https://img.shields.io/badge/Java-8%2B-blue.svg)](#)
[![Maven Central](https://img.shields.io/badge/Maven-1.0.0-green.svg)](#)
[![Zero-APT](https://img.shields.io/badge/APT-None-orange.svg)](#)

> **Java-ORM** 是一个面向 Java 8+ 的轻量级现代关系型数据库 ORM 框架。
> 它借鉴了 **Easy-Query** 的设计哲学，但**彻底摒弃了编译期 APT（代码生成器）技术**。
> 通过基于 **ByteBuddy 的运行时代理调用录制（Invocation Recording）**，实现了类似 `s.getTeacher().getAge()` 的多级级联字段感知、编译期类型安全与纯粹的 SQL 链式流，实体保持纯净 POJO（`@Column` 完全可选）。

---

## 核心特性

- 🚀 **零 APT 编译依赖**：无需在 `pom.xml` 中配置任何 annotation processor，无须生成 `QUser` 或 `UserProxy` 冗余代码，代码随写随测，实体即改即用。
- 🔍 **多级字段感知与 IDE 原生重构**：直接写 `s -> s.getTeacher().getAge()`，IDE 自动补全字段，字段重命名时全局安全重构。
- 🌊 **纯粹贴近 SQL 的链式流**：抛弃繁琐难看的 `$(...)` 包裹语法，操作符直接跟在字段后面：`.where().and(s -> s.getAge()).ge(18).and(s -> s.getTeacher().getAge()).gt(30)`。
- 🔗 **智能导航与多表自动 JOIN**：在条件或排序列中点号调用外键关联实体属性，框架自动基于 JPA / 外键约定生成对应表的 `LEFT JOIN`。
- 📦 **高性能 Split/Batch IN 装配（Include）**：一对一/多对一/一对多关系通过 `.include()` 显式批量加载（IN 查询），从根源杜绝 SQL 笛卡尔积膨胀与分页失效问题。
- 🏷️ **极低侵入性（@Column 完全可选）**：预设全局 `NameConversion` 策略（默认驼峰转下划线），标准 POJO 无需任何额外标记即可映射数据表。
- 🛡️ **无感逻辑删除与多租户**：标记 `@LogicDelete` 或全局配置后，所有查询自动追加未删除过滤，删除操作自动重写为 `UPDATE`。
- ☕ **向下兼容 Java 8**：基线面向 Java 8，无缝向上兼容运行于 Java 11 / 17 / 21 / 25。

---

## 快速上手

### 1. 引入 Maven 依赖

```xml
<dependency>
    <groupId>com.javaorm</groupId>
    <artifactId>java-orm-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 实体定义（无需生成任何代理代码）

```java
import com.javaorm.core.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@Table(name = "t_teacher")
public class Teacher {
    @Id
    private Long id;
    
    private String teacherName; // 自动映射为 teacher_name
    private Integer age;
    private String title;

    private List<Student> students; // 一对多关联

    // Getter / Setter ...
}

@Table(name = "t_student")
public class Student {
    @Id
    private Long id;

    private String studentName; // 自动映射为 student_name
    private Integer age;
    private Long teacherId;

    private Teacher teacher; // 多对一关联（默认匹配 teacherId -> Teacher.id）

    @LogicDelete(normalValue = "0", deletedValue = "1")
    private Integer isDeleted;

    private LocalDateTime createTime;

    // Getter / Setter ...
}
```

### 3. 初始化客户端

```java
DataSource dataSource = ...; // 任何 JDBC DataSource (HikariCP / Druid / SQLite 等)
JavaOrm orm = JavaOrm.create(dataSource);
```

---

## 常用操作代码示例

### 1. 基础单表查询

```java
List<Student> list = orm.select(Student.class)
    .where()
        .and(s -> s.getAge()).ge(18)
        .and(s -> s.getStudentName()).like("张%")
    .orderBy(s -> s.getAge()).desc()
    .page(1, 10)
    .list();
```

### 2. 级联导航字段过滤（自动隐式 LEFT JOIN）

直接点出两层实体关系，框架自动根据外键生成 `LEFT JOIN t_teacher t1 ON t0.teacher_id = t1.id`：

```java
List<Student> students = orm.select(Student.class)
    .where()
        .and(s -> s.getTeacher().getAge()).gt(35)
    .list();
```

### 3. 关联作用域子块（whereRelation）

```java
List<Student> students = orm.select(Student.class)
    .whereRelation(s -> s.getTeacher(), t -> t
        .where()
            .and(Teacher::getTitle).eq("教授")
    )
    .list();
```

### 4. 关联数据装配（Include 模式，避免笛卡尔积）

```java
// 多对一：查出学生，并装配每个学生的指导老师对象
List<Student> students = orm.select(Student.class)
    .include(s -> s.getTeacher())
    .where()
        .and(s -> s.getAge()).ge(18)
    .list();

// 一对多：查出老师，并装配名下的所有学生集合 List<Student>
List<Teacher> teachers = orm.select(Teacher.class)
    .include(t -> t.getStudents())
    .list();
```

### 5. 增删改（CUD 操作）

```java
// 实体级插入（自增主键自动回填）
Student s = new Student();
s.setStudentName("王小明");
s.setAge(20);
orm.insert(s);

// 实体级更新（仅更新非空字段）
s.setAge(21);
orm.update(s);

// 实体级删除（标有 @LogicDelete 则自动执行 UPDATE is_deleted = 1）
orm.delete(s);

// 纯 SQL 批量流式条件更新（支持直接代理属性、自增/自减计算与表达式）
Student s = orm.proxy(Student.class);
orm.update(Student.class)
    .set(s.getAge(), increment(1))   // 原地自增 age = age + 1
    .set(s.getStudentName(), "新名字")
    .where()
        .and(s.getStudentName()).eq("王大锤")
        .andGroup((w, stu) -> {     // 无需外部 proxy，直接注入 (w, stu)
            w.and(stu.getAge()).ge(18);
            w.or(stu.getAge()).isNull();
        })
    .execute();

// 纯 SQL 批量流式条件删除（有 @LogicDelete 自动转逻辑删除）
orm.delete(Student.class)
    .where()
        .and(s.getAge()).lt(10)
    .execute();
```

---

## 高级 SQL 特性支持

### 1. SQL 函数与 Group By / Having

支持 `count`、`sum`、`avg`、`min`、`max`、`length` 等标准 SQL 函数，并可直接在投影、`where`、`having` 中使用：

```java
import static com.javaorm.core.SQL.*;

Student s = orm.proxy(Student.class);

// 自定义投影 + GROUP BY + HAVING
List<Map<String, Object>> list = orm.select(count(s.getId()).as("total"), s.getTeacherId())
    .from(Student.class)
    .groupBy(s.getTeacherId())
    .having()
        .and(count(s.getId())).ge(2)
    .orderBy(s.getTeacherId()).asc()
    .listMaps();
```

### 2. DTO 投影模式

支持直接映射到业务 DTO（自动驼峰/下划线字段映射与类型转换）：

```java
// 方式 A：orm.select(DTO.class).from(Entity.class).select(...)
List<StatDto> dtoListA = orm.select(StatDto.class)
    .from(Student.class)
    .select(count(s.getId()).as("total"), s.getTeacherId(), avg(s.getAge()).as("avgAge"))
    .groupBy(s.getTeacherId())
    .having()
        .and(count(s.getId())).ge(2)
    .list();

// 方式 B：orm.select(DTO.class, expr1, expr2...).from(Entity.class)
List<StatDto> dtoListB = orm.select(StatDto.class, count(s.getId()).as("total"), s.getTeacherId())
    .from(Student.class)
    .groupBy(s.getTeacherId())
    .list();
```

### 3. 子查询与 EXISTS

支持类型安全的 IN 子查询与相关联 EXISTS 语法：

```java
Student s = orm.proxy(Student.class);
Teacher t = orm.proxy(Teacher.class);

// 3.1 IN 子查询
List<Student> students = orm.select(Student.class)
    .where()
        .and(s.getTeacherId()).in(
            orm.select(t.getId())
               .from(Teacher.class)
               .where()
                   .and(t.getAge()).gt(40)
        )
    .list();

// 3.2 EXISTS 关联子查询
List<Student> existsStudents = orm.select(Student.class)
    .where()
        .andExists(
            orm.select(t.getId())
               .from(Teacher.class)
               .where()
                   .and(t.getId()).eq(s.getTeacherId())
                   .and(t.getTitle()).eq("讲师")
        )
    .list();
```

### 4. UNION / UNION ALL

```java
List<Map<String, Object>> unionResult = orm.select(s.getStudentName())
    .from(Student.class)
    .where()
        .and(s.getAge()).le(20)
    .union(
        orm.select(s.getStudentName())
           .from(Student.class)
           .where()
               .and(s.getAge()).ge(22)
    )
    .listMaps();
```

### 5. 开窗函数 (Window Functions)

支持 SQL 标准及 Oracle 全量开窗函数，通过链式 `.over().partitionBy(...).orderBy(...).rowsBetween(...)` 构建：

```java
Student s = orm.proxy(Student.class);

// 1. 排名开窗函数：ROW_NUMBER, RANK, DENSE_RANK, NTILE, PERCENT_RANK, CUME_DIST
List<Map<String, Object>> rankList = orm.select(Student.class).select(
    s.getId(),
    s.getStudentName(),
    s.getAge(),
    rowNumber().over().partitionBy(s.getTeacherId()).orderBy(s.getAge()).asc().as("rn"),
    rank().over().partitionBy(s.getTeacherId()).orderBy(s.getAge()).asc().as("rk"),
    denseRank().over().partitionBy(s.getTeacherId()).orderBy(s.getAge()).asc().as("drk"),
    ntile(3).over().partitionBy(s.getTeacherId()).orderBy(s.getAge()).asc().as("nt")
).maps();

// 2. 取值/位移开窗函数：LEAD, LAG, FIRST_VALUE, LAST_VALUE, NTH_VALUE
List<Map<String, Object>> navList = orm.select(Student.class).select(
    s.getId(),
    s.getAge(),
    lead(s.getAge(), 1, 0).over().partitionBy(s.getTeacherId()).orderBy(s.getAge()).asc().as("next_age"),
    lag(s.getAge(), 1, 0).over().partitionBy(s.getTeacherId()).orderBy(s.getAge()).asc().as("prev_age"),
    firstValue(s.getAge()).over().partitionBy(s.getTeacherId()).orderBy(s.getAge()).asc().as("first_age")
).maps();

// 3. 聚合开窗与滑动窗口 (ROWS BETWEEN)
List<Map<String, Object>> aggList = orm.select(Student.class).select(
    s.getId(),
    s.getAge(),
    sum(s.getAge()).over().partitionBy(s.getTeacherId()).as("dept_sum"),
    // 累计求和: ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    sum(s.getAge()).over().partitionBy(s.getTeacherId()).orderBy(s.getAge()).asc().rowsUnboundedPreceding().as("running_sum"),
    // 全表开窗统计
    count().over().as("grand_count")
).maps();

// 4. 自动装配到 DTO
List<StudentWindowDto> dtoList = orm.select(StudentWindowDto.class).from(Student.class).select(
    s.getId(),
    s.getStudentName(),
    rowNumber().over().partitionBy(s.getTeacherId()).orderBy(s.getAge()).asc().as("rowNum"),
    sum(s.getAge()).over().partitionBy(s.getTeacherId()).as("sumAge")
).list();
```

### 6. 多数据库方言支持与 Oracle 12c+

支持主流数据库方言切换：`MySQLDialect`, `PostgreSQLDialect`, `OracleDialect`, `SQLiteDialect`, `H2Dialect`。

针对 Oracle 提供专属方言支持：
- 严格遵循 Oracle 双引号标识符转义（`"TABLE"`、`"COLUMN"`）
- 原生 Oracle 12c+ ANSI 分页语法（`OFFSET ? ROWS FETCH NEXT ? ROWS ONLY`）
- 无表虚拟查询自动注入 `FROM DUAL`
- 函数智能映射：`NOW()` -> `SYSDATE`，`IFNULL()` -> `NVL()`，`CONCAT()` -> `||`，`SUBSTRING()` -> `SUBSTR()`

```java
// 初始化 Oracle 方言 ORM 客户端
JavaOrm orm = JavaOrm.create(dataSource, new OracleDialect());

// 原生支持 Oracle 12c+ 链式分页
List<Student> pageStudents = orm.select(Student.class)
    .orderBy(s.getId()).asc()
    .page(1, 10) // 生成 OFFSET 0 ROWS FETCH NEXT 10 ROWS ONLY
    .list();
```

### 7. 原地更新与自增算术表达式 (UPDATE / DELETE)

```java
// 7.1 原地自增/自减更新: age = age + 1, age = age - 5, age = age * 2
orm.update(Student.class)
    .set(s.getAge(), increment(1))
    .where()
        .and(s.getId()).eq(1L)
    .execute();

// 7.2 双参数作用域条件更新与删除: (w, s)
orm.update(Student.class)
    .set(s.getAge(), 20)
    .where((w, s) -> w.and(s.getAge()).lt(18))
    .execute();

orm.delete(Student.class)
    .where((w, s) -> w.and(s.getAge()).ge(60))
    .execute();
```

---

## 运行录制原理解析

```
业务代码                                  ByteBuddy 拦截器                     ThreadLocal 录制栈
   |                                              |                                   |
   |-- and(s -> s.getTeacher().getAge()) -------->|                                   |
   |                                              |-- 拦截 getTeacher() ------------->| 记录: "teacher"
   |                                              |-- 返回 Teacher 代理 ------------->|
   |                                              |-- 拦截 teacher.getAge() --------->| 记录: "age"
   |                                              |-- 返回默认值 (0) -----------------|
   |<-- 返回 PropertyOperator(teacher.age) -------|                                   |
   |                                                                                  |
   |-- .gt(30) ----------------------------------> 构建 SQL: t1.age > 30               |
```

1. **单例代理池**：每个实体的代理子类仅在首次使用时生成一次，且实例在内存中单例复用，执行查询录制时**零额外 GC 分配**。
2. **纳秒级开销**：录制调用仅执行空方法拦截与 ThreadLocal 字符串记录，每次条件构建仅需数十纳秒，性能无限逼近手写 SQL。
