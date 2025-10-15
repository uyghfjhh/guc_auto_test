
# 1. 要求
参数session的测试用例，要求：
- 使用jdbc pgdirver实现，支持dsn可配
- 每个用例一个函数
- 测试结果使用表格展示
- 用例中标出的检测点，打印出日志，要清晰的说明是哪个客户端连接，哪个后端连接，guc参数的值。如果检测点检测失败，则用例执行失败，打出期望结果和实际结果
- 使用红色日志，打印出连接中执行的sql命令

# 2. 测试用例



## 2.1 用例1：测试非guc report参数同步

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
show extra_float_digits -- 检测点：这里显示的应该是默认值1，因为这里执行了guc参数同步，reset extra_float_digits


```

(3) 连接1继续执

```sql
begin;

SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user;
--  这里返回的应该是另一个后端连接，之前的后端连接被连接2用了
show extra_float_digits -- 检测点： 返回的是之前设置的值3，而不是1，因为这里执行了guc参数同步，生成了set extra_float_digits=3发送给后端 连接了
end; 
```





