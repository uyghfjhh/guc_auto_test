# GUC参数自动化测试

## 项目说明

本项目用于测试PostgreSQL连接池中GUC参数的同步机制，验证当连接复用时，GUC参数是否正确同步。

## 测试场景

### 用例1：测试非guc report参数同步

本测试使用**两种PostgreSQL协议**分别执行相同的测试场景：

#### 🔵 Simple Query Protocol (简单查询协议)
- 使用 `Statement` 执行SQL
- 每次发送一条SQL命令到服务器
- 适用于简单的查询场景

#### 🟢 Extended Query Protocol (扩展查询协议)
- 使用 `PreparedStatement` 执行SQL
- 支持参数化查询、预编译语句
- Parse-Bind-Execute 三阶段执行模式
- 适用于复杂查询和重复执行的场景

### 测试流程

1. **客户端连接1**：设置 `extra_float_digits = 3`，记录后端连接信息
2. **客户端连接2**：复用连接1的后端连接，验证参数是否被重置为默认值
3. **客户端连接1继续**：获得新的后端连接，验证参数是否同步为之前设置的值3

### 检测点

每种协议都会执行4个检测点：
- **检测点1**：连接2是否复用了连接1的后端连接（通过pid比较）
- **检测点2**：连接2中的参数是否被重置为默认值（执行了RESET）
- **检测点3**：连接1是否获得了新的后端连接
- **检测点4**：连接1在新后端中参数是否恢复为设置的值3（执行了SET）

总共会产生 **8个测试结果**（2种协议 × 4个检测点）

## 项目结构

```
guc-auto-test/
├── pom.xml
├── README.md
├── doc/
│   └── guc参数测试用例.md
└── src/
    └── test/
        ├── java/com/fbasecman/guc/
        │   ├── config/
        │   │   └── DatabaseConfig.java      # 数据库配置类
        │   ├── model/
        │   │   └── TestResult.java          # 测试结果模型
        │   ├── util/
        │   │   └── TablePrinter.java        # 表格打印工具
        │   └── GucSyncScenarioTest.java     # 主测试类
        └── resources/
            └── db.properties.template        # 数据库配置模板
```

## 环境要求

- JDK 17+
- Maven 3.6+
- PostgreSQL 数据库（支持连接池的版本，如PgBouncer等）

## 配置说明

1. 复制配置文件模板：
```bash
cp src/test/resources/db.properties.template src/test/resources/db.properties
```

2. 编辑 `db.properties` 文件，配置数据库连接信息：
```properties
db.url=jdbc:postgresql://your-host:your-port/your-database
db.user=your-username
db.password=your-password
```

## 运行测试

### 方式1：使用Maven运行（推荐）
```bash
mvn clean compile exec:java
```

### 方式2：使用JUnit运行
```bash
mvn clean test
```

### 方式3：编译后直接运行
```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="com.fbasecman.guc.GucSyncScenarioTest"
```

## 输出说明

测试执行过程中会输出：
- **红色日志**：执行的SQL命令
- **黄色日志**：测试步骤说明
- **蓝色日志**：后端连接信息
- **绿色日志**：检测点通过
- **红色日志**：检测点失败

最后会输出一个表格，汇总所有检测点的测试结果。

## 测试结果示例

```
========================================================================================================================
GUC参数测试结果汇总
========================================================================================================================
测试用例                              参数名                     期望值          实际值          结果      备注                            
------------------------------------------------------------------------------------------------------------------------
用例1-Simple协议-检测点1              后端连接复用                pid相同         pid相同         ✓ 通过    连接2应复用连接1的后端连接        
用例1-Simple协议-检测点2              extra_float_digits          1               1               ✓ 通过    连接2中参数应被重置为默认值       
用例1-Simple协议-检测点3              后端连接变更                新后端连接       新后端连接       ✓ 通过    连接1应获得新的后端连接          
用例1-Simple协议-检测点4              extra_float_digits          3               3               ✓ 通过    连接1在新后端中参数应恢复为之前设置的值
用例1-Extended协议-检测点1            后端连接复用                pid相同         pid相同         ✓ 通过    连接2应复用连接1的后端连接        
用例1-Extended协议-检测点2            extra_float_digits          1               1               ✓ 通过    连接2中参数应被重置为默认值       
用例1-Extended协议-检测点3            后端连接变更                新后端连接       新后端连接       ✓ 通过    连接1应获得新的后端连接          
用例1-Extended协议-检测点4            extra_float_digits          3               3               ✓ 通过    连接1在新后端中参数应恢复为之前设置的值
========================================================================================================================
总计: 8 | 通过: 8 | 失败: 0 | 通过率: 100.00%
========================================================================================================================
```

## 技术要点

1. **双协议支持**：
   - Simple Query Protocol：使用 `Statement` 执行SQL
   - Extended Query Protocol：使用 `PreparedStatement` 执行SQL
   - 两种协议分别测试，验证GUC参数同步在不同协议下的一致性

2. **连接管理**：使用JDBC创建多个客户端连接

3. **事务控制**：使用 `setAutoCommit(false)` 和 `commit()` 控制事务

4. **后端连接检测**：通过 `pg_backend_pid()` 识别后端连接

5. **参数同步验证**：通过 `SHOW` 命令获取GUC参数值

6. **结果验证**：对比期望值和实际值，记录测试结果

## PostgreSQL协议说明

### Simple Query Protocol vs Extended Query Protocol

| 特性 | Simple Protocol | Extended Protocol |
|------|-----------------|-------------------|
| JDBC接口 | `Statement` | `PreparedStatement` |
| 执行流程 | 单步执行 | Parse → Bind → Execute |
| 参数化 | 不支持 | 支持参数化查询 |
| 预编译 | 否 | 是 |
| 适用场景 | 简单查询 | 复杂查询、重复执行 |
| SQL注入防护 | 需手动处理 | 自动处理 |

在连接池环境中，两种协议的GUC参数同步行为可能存在差异，因此需要分别测试验证。

## 扩展说明

可以根据需要扩展更多测试用例，例如：
- 测试GUC report参数的同步
- 测试SIGHUP级别参数的变更
- 测试postmaster级别参数的限制
- 测试不同用户的参数隔离

