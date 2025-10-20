
# 1. 要求
参数session的测试用例，要求：
- 使用jdbc pgdirver实现，支持dsn可配
- 每个用例一个函数
- 测试结果使用表格展示
- 用例中标出的检测点，打印出日志，要清晰的说明是哪个客户端连接，哪个后端连接，guc参数的值。如果检测点检测失败，则用例执行失败，打出期望结果和实际结果
- 使用红色日志，打印出连接中执行的sql命令

# 2. 测试用例



## 2.1 测试类别：测试非guc report参数同步

### 2.1.1 测试用例1：extra_float_digits参数
（1）客户端连接1中执行 

```sql
begin;
show extra_float_digits -- 返回初始值1
SET extra_float_digits = 3; -- 设置extra_float_digits=3，这里假设分配的后端连接1
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
-- 这里记录下后端连接的 pid，ip，端口，extra_float_digits的值
end; -- 事务解释之后，才能启动连接2，这里不能关闭连接1，
```

 （2）客户端 连接2中执行：

```sql
begin;
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;-- 检测点：这里会复用连接1的后端连接
show extra_float_digits -- 检测点：这里显示的应该是默认值1，因为这里执行了guc参数重置，reset extra_float_digits
-- 注意这里事务不结束

```

(3) 连接1继续执

```sql
begin;

SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
--  这里返回的应该是另一个后端连接，之前的后端连接被连接2用了
show extra_float_digits -- 检测点： 返回的是之前设置的值3，而不是1，因为这里执行了guc参数同步，生成了set extra_float_digits=3发送给后端 连接了
end; 
```





## 2.1 测试类别：guc report参数同步

> **说明**：以下所有用例均需分别在 Simple 协议与 Extended 协议下执行，执行顺序、检测点与日志输出保持一致。涉及的客户端连接编号、后端连接 (pid/ip/port) 以及参数值均需在日志中明确记录。

### 2.1.1 测试用例1：DateStyle参数——后端复用与参数同步

（1）客户端连接1执行：

```sql
-- 开启事务并记录初始状态

show DateStyle; -- 检测点1：期望值 ISO, MDY，日志记录“客户端1-检测点1”

-- 修改DateStyle并记录后端连接信息
SET DateStyle = 'ISO, DMY'; -- 检测点2：日志打印执行SQL（红色），确保使用SET而非ALTER
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
-- 检测点3：记录后端连接标识与DateStyle=ISO, DMY


```

（2）客户端连接2执行：

```sql
begin;
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
-- 检测点4：确认复用客户端1的后端连接，日志需比对pid/ip/port

show DateStyle;
-- 检测点5：期望返回默认值 ISO, MDY，说明连接池在发放后端连接前执行了RESET/ParameterStatus，同步日志打印实际值
-- 注意：此处保持事务未结束，不释放后端连接
```

（3）客户端连接1再次执行：

```sql
begin;
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
-- 检测点6：应分配新的后端连接，pid/ip/port 与步骤(1)/(2)不同

show DateStyle;
-- 检测点7：期望返回 ISO, DMY。验证缓存同步逻辑会对新后端执行 SET，日志需明确显示同步行为

commit;
```

（4）客户端连接2收尾：

```sql
show DateStyle; -- 检测点8：仍应为 ISO, MDY
commit; -- 释放后端连接
```

### 2.1.2 测试用例2：TimeZone参数——RESET 恢复默认值

--  这里要注意SET/RESET在事务中执行，就无法同步

（1）客户端连接1执行：

```sql
show TimeZone; -- 检测点1：记录默认值，为 Asia/Shanghai 或 UTC（以实际环境为准）
SET TimeZone = UTC; -- 检测点2：设置新值并输出SQL日志
show TimeZone; -- 确保设置成功
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
-- 检测点3：记录后端连接标识与 TimeZone=UTC;
```

（2）客户端连接2执行：

```sql
begin;
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
-- 检测点4：确认复用步骤(1)的后端连接


show TimeZone; -- 检测点5： 期望默认值Asia/Shanghai
-- 保持事务未提交，继续占用后端连接
```

（3）客户端连接1再次执行：

```sql
begin;
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user; -- 确实是新分配的后端连接
show TimeZone; -- 检测点6：期望值依然是 UTC
commit;

RESET TimeZone；
show TimeZone；-- 检测点7：恢复默认值 Asia/Shanghai 
```

（4）客户端连接2执行：

```sql
commit; -- 释放后端连接
```



（5）客户端连接1

```sql
begin;
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user; -- 应该复用客户端连接2的后端连接
show TimeZone；-- 检测点8：还是默认值 Asia/Shanghai 
end;
```

## 2.3 测试类别 ：多参数同步与 RESET ALL

目标：验证多个 guc report 参数在 RESET ALL 后是否按照路由默认值重新同步。

（1）客户端连接1执行：

```sql
SET client_encoding = 'GBK'; --  默认值UTF8
SET standard_conforming_strings = off; --  默认值UTF8
SET IntervalStyle = 'sql_standard'; -- 默认值 postgres
SET DateStyle = 'ISO, DMY'; --  默认值 ISO, MDY
set extra_float_digits = 3; -- 默认值1
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
-- 检测点1：日志中记录所有已修改的参数和值
RESET ALL;
```

（2）客户端连接2执行：

```sql
begin;
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
-- 检测点2：确认复用步骤(1)的后端连接
-- 检测点3：下数值应全为默认值
SHOW client_encoding; 
SHOW standard_conforming_strings;
SHOW IntervalStyle;
SHOW DateStyle;
SHOW extra_float_digits;
-- 保持事务未提交，继续占用后端连接
```

（3）客户端连接1再次执行：

```sql
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
-- 检测点4：应分配新的后端连接

-- 检测点5：下数值应全为默认值
SHOW client_encoding; 
SHOW standard_conforming_strings;
SHOW IntervalStyle;
SHOW DateStyle;
SHOW extra_float_digits;
```

（4）客户端连接2收尾：

```sql
commit; -- 释放后端连接
```



## 2.4 测试类别 ：多参数同步与 RESET ALL

目标：验证多个 guc report 参数在 RESET ALL 后是否按照路由默认值重新同步。

（1）客户端连接1执行：

```sql
SET standard_conforming_strings = off; --  默认值UTF8
SET IntervalStyle = sql_standard; -- 默认值 postgres
SET DateStyle = ISO, DMY; --  默认值 ISO, MDY
set extra_float_digits = 3; -- 默认值1
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
-- 检测点1：日志中记录所有已修改的参数和值
DISCARD ALL;
```

（2）客户端连接2执行：

```sql
begin;
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
-- 检测点2：确认复用步骤(1)的后端连接
-- 检测点3：下数值应全为默认值
SHOW standard_conforming_strings;
SHOW IntervalStyle;
SHOW DateStyle;
SHOW extra_float_digits;
-- 保持事务未提交，继续占用后端连接
```

（3）客户端连接1再次执行：

```sql
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
-- 检测点4：应分配新的后端连接

-- 检测点5：下数值应全为默认值
SHOW standard_conforming_strings;
SHOW IntervalStyle;
SHOW DateStyle;
SHOW extra_float_digits;
```

（4）客户端连接2收尾：

```sql
commit; -- 释放后端连接
```

## 2.5 测试类别 ：事务中set guc

目标：测试事务中修改guc参数不会保存到guc缓存，也就不会触发连接前后的guc前后端参数同步

（1）客户端连接1执行：

```sql
begin;
SET standard_conforming_strings = off; --  默认值UTF8
SET IntervalStyle = sql_standard; -- 默认值 postgres
SET DateStyle = ISO, DMY; --  默认值 ISO, MDY
set extra_float_digits = 3; -- 默认值1
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
-- 检测点1：日志中记录所有已修改的参数和值
commit;
```

（2）客户端连接2执行：

```sql
begin;
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
-- 检测点2：确认复用步骤(1)的后端连接
-- 检测点3：下数值因为步骤（1）中修改的值，没同步
SHOW standard_conforming_strings;
SHOW IntervalStyle;
SHOW DateStyle;
SHOW extra_float_digits;
-- 保持事务未提交，继续占用后端连接
```

（3）客户端连接1再次执行：

```sql
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
-- 检测点4：应分配新的后端连接

-- 检测点5：下数值应全为默认值,因为步骤（1）中修改的值，没同步
SHOW standard_conforming_strings;
SHOW IntervalStyle;
SHOW DateStyle;
SHOW extra_float_digits;
```

（4）客户端连接2收尾：

```sql
commit; -- 释放后端连接
```



## 2.6测试类别 ：测试大量guc参数同步

目标：测试大量guc参数同步的场景的 是否能处理正确

1）客户端连接1中执行 

```sql
---设置大量的GUC参数，至少100个，
SET extra_float_digits = 3; -- 设置extra_float_digits=3，这里假设分配的后端连接1
....
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
-- 检测点1：记录下设置的guc参数的值

```

 （2）客户端 连接2中执行：

```sql
begin;
SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;-- 检测点2：这里会复用连接1的后端连接
show extra_float_digits -- 检测点3：这里显示的guc参数的值应该还是为默认值
....
```

(3) 连接1继续执

```sql
begin;

SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
--  检测点4: 分配新的后端连接
show extra_float_digits -- 检测点4： 返回的是之前设置的值，非默认值
...

end; 
```

（4）客户端连接2收尾：

```sql
commit; -- 释放后端连接
```





