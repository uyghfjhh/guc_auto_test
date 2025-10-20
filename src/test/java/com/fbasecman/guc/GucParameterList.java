package com.fbasecman.guc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GUC参数列表 - 包含100个可设置的GUC参数及其默认值和测试值
 * 
 * 注意：这里的参数名称和默认值是手工编写的，仅供参考。
 * 实际使用时，建议使用 getGucParametersFromDatabase() 方法从数据库动态获取。
 */
public class GucParameterList {
    
    /**
     * 从数据库动态获取可设置的GUC参数
     * 这个方法会查询 pg_settings 视图获取真实的参数信息
     */
    public static Map<String, String[]> getGucParametersFromDatabase(Connection conn, int limit) throws Exception {
        Map<String, String[]> params = new LinkedHashMap<>();
        
        // 排除危险参数：
        // 1. JDBC驱动敏感参数 - 不允许修改
        // 2. session_authorization, role - 会话授权/角色切换
        // 3. 服务器级别参数 - 需要特殊权限或重启
        // 4. 已废弃的参数 - PostgreSQL 12+
        String sql = "SELECT name, setting, unit, context, min_val, max_val, vartype " +
                     "FROM pg_settings " +
                     "WHERE context IN ('user', 'superuser') " +  // 只获取用户可设置的参数
                     "AND name NOT LIKE 'pg_%' " +  // 排除内部参数
                     "AND name NOT LIKE '%.%' " +  // 排除扩展参数（如 fdd.time_diff, auto_explain.log_min_duration）
                     "AND name NOT IN (" +
                     "  'client_encoding', " +  // JDBC驱动强制要求UTF8
                     "  'DateStyle', " +  // JDBC驱动要求以ISO开头，不允许修改
                     "  'TimeZone', " +  // JDBC驱动敏感参数
                     "  'IntervalStyle', " +  // JDBC驱动敏感参数
                     "  'session_authorization', " +  // 会话授权
                     "  'role', " +  // 角色切换
                     "  'listen_addresses', " +  // 服务器级别
                     "  'port', " +  // 服务器级别
                     "  'max_connections', " +  // 服务器级别
                     "  'shared_buffers', " +  // 服务器级别
                     "  'wal_level', " +  // 服务器级别
                     "  'max_wal_senders', " +  // 服务器级别
                     "  'max_replication_slots', " +  // 服务器级别
                     "  'default_tablespace', " +  // 可能不存在的表空间
                     "  'default_with_oids', " +  // PostgreSQL 12+已废弃
                     "  'replacement_sort_tuples', " +  // PostgreSQL 11+已废弃
                     "  'vacuum_cleanup_index_scale_factor', " +  // 可能引起问题
                     "  'idle_in_transaction_session_timeout', " +  // 会导致事务内超时断开连接
                     "  'idle_session_timeout', " +  // 会导致会话超时断开连接
                     "  'statement_timeout', " +  // 会导致语句超时
                     "  'lock_timeout' " +  // 会导致锁超时
                     ") " +
                     "ORDER BY name " +
                     "LIMIT " + limit;
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String name = rs.getString("name");
                String currentValue = rs.getString("setting");
                String unit = rs.getString("unit");
                String minVal = rs.getString("min_val");
                String maxVal = rs.getString("max_val");
                String vartype = rs.getString("vartype");
                
                // 为每个参数生成一个测试值（与当前值不同，且在有效范围内）
                String testValue = generateTestValue(name, currentValue, unit, minVal, maxVal, vartype);
                
                params.put(name, new String[]{currentValue, testValue});
            }
        }
        
        return params;
    }
    
    /**
     * 根据参数名称和当前值生成一个不同的测试值
     * 注意：某些参数的值需要带引号，某些不需要，要严格遵循PostgreSQL的SET语法
     * 
     * @param name 参数名
     * @param currentValue 当前值
     * @param unit 单位
     * @param minVal 最小值（可能为null）
     * @param maxVal 最大值（可能为null）
     * @param vartype 参数类型（bool, integer, real, string, enum）
     */
    private static String generateTestValue(String name, String currentValue, String unit, 
                                           String minVal, String maxVal, String vartype) {
        // 对于危险参数，保持原值不变（虽然测试值和默认值相同，但避免设置失败）
        if (name.equals("client_encoding") ||  // JDBC驱动强制要求UTF8
            name.equals("DateStyle") ||  // JDBC驱动要求以ISO开头，不允许修改
            name.equals("TimeZone") ||  // JDBC驱动敏感参数
            name.equals("IntervalStyle") ||  // JDBC驱动敏感参数
            name.equals("session_authorization") ||  // 会话授权
            name.equals("role") ||  // 角色切换
            name.equals("default_tablespace")) {  // 可能不存在的表空间
            // 保持原值
            if (currentValue == null || currentValue.isEmpty()) {
                return "''";
            }
            // 如果原值已经有引号，直接返回
            if (currentValue.startsWith("'") && currentValue.endsWith("'")) {
                return currentValue;
            }
            // 添加引号返回
            return "'" + currentValue + "'";
        }
        
        // 处理空字符串的情况 - 返回一个安全的非空测试值
        if (currentValue == null || currentValue.isEmpty()) {
            // 对于application_name等允许空值的参数，使用带引号的字符串
            if (name.equals("application_name")) {
                return "'test_value'";
            }
            return "'test_value'";
        }
        
        // 布尔类型参数 - 不需要引号
        if ("on".equalsIgnoreCase(currentValue)) {
            return "off";
        } else if ("off".equalsIgnoreCase(currentValue)) {
            return "on";
        }
        
        // 优先处理：必须作为浮点数的参数（即使值看起来像整数）
        if (name.equals("geqo_selection_bias")) {
            // 有效范围: 1.5 到 2.0
            try {
                double val = Double.parseDouble(currentValue);
                return val >= 1.8 ? "1.5" : "2.0";
            } catch (NumberFormatException e) {
                return "1.5";
            }
        } else if (name.equals("geqo_seed")) {
            // 有效范围: 0 到 1.0
            try {
                double val = Double.parseDouble(currentValue);
                return val == 0.0 ? "0.5" : "0.0";
            } catch (NumberFormatException e) {
                return "0.5";
            }
        } else if (name.equals("cursor_tuple_fraction")) {
            // 有效范围: 0.0 到 1.0
            try {
                double val = Double.parseDouble(currentValue);
                return val <= 0.3 ? "0.5" : "0.1";
            } catch (NumberFormatException e) {
                return "0.5";
            }
        }
        
        // 数值类型参数 - 不需要引号
        try {
            int intValue = Integer.parseInt(currentValue);
            
            // 优先使用数据库提供的范围信息
            if (minVal != null && maxVal != null && !minVal.isEmpty() && !maxVal.isEmpty()) {
                try {
                    int min = Integer.parseInt(minVal);
                    int max = Integer.parseInt(maxVal);
                    
                    // 在有效范围内生成一个不同的值
                    if (intValue == min) {
                        // 当前是最小值，返回一个稍大的值
                        return String.valueOf(Math.min(min + 1, max));
                    } else if (intValue == max) {
                        // 当前是最大值，返回一个稍小的值
                        return String.valueOf(Math.max(max - 1, min));
                    } else if (intValue < (min + max) / 2) {
                        // 当前值在下半部分，返回上半部分的值
                        return String.valueOf(Math.min(intValue + (max - min) / 4, max));
                    } else {
                        // 当前值在上半部分，返回下半部分的值
                        return String.valueOf(Math.max(intValue - (max - min) / 4, min));
                    }
                } catch (NumberFormatException e) {
                    // min/max不是整数，继续使用原来的逻辑
                }
            }
            
            // 特殊处理：有范围限制的GEQO参数
            if (name.equals("geqo_effort")) {
                // 有效范围: 1 到 10
                return intValue >= 5 ? String.valueOf(intValue - 2) : String.valueOf(intValue + 2);
            } else if (name.equals("geqo_threshold")) {
                // 有效范围: 2 到 INT_MAX，使用加减法
                return intValue >= 12 ? "10" : "12";
            } else if (name.contains("flush_after")) {
                // backend_flush_after, checkpoint_flush_after, bgwriter_flush_after
                // 有效范围通常是 0 到 256 (8kB块)
                if (intValue == 0) {
                    return "128";  // 使用中间值
                } else if (intValue > 128) {
                    return "64";
                } else {
                    return "128";
                }
            } else if (name.contains("_delay") && intValue == 0) {
                // 各种delay参数，通常有较小的上限
                return "10";  // 使用小值而不是1000
            } else if (name.equals("max_parallel_workers_per_gather") || 
                       name.equals("max_parallel_workers") ||
                       name.equals("max_worker_processes")) {
                // 并行worker相关参数，通常上限较小
                if (intValue == 0) {
                    return "2";
                } else if (intValue < 4) {
                    return String.valueOf(intValue + 1);
                } else {
                    return String.valueOf(intValue - 1);
                }
            } else if (name.equals("log_rotation_age") && intValue == 0) {
                // 日志轮转时间，0表示禁用
                return "1440";  // 24小时（分钟）
            } else if (name.equals("log_rotation_size") && intValue == 0) {
                // 日志轮转大小，0表示禁用
                return "10240";  // 10MB (kB)
            } else if (name.equals("extra_float_digits")) {
                // 有效范围: -15 到 3
                if (intValue >= 0) {
                    return intValue >= 2 ? "-1" : "2";
                } else {
                    return "0";
                }
            } else if (name.equals("log_min_duration_sample")) {
                // 有效范围: -1 到 INT_MAX，但通常不会设置很大的值
                if (intValue == -1) {
                    return "100";  // 100ms
                } else if (intValue == 0) {
                    return "50";
                } else {
                    return "-1";  // 禁用
                }
            } else if (name.equals("temp_file_limit")) {
                // 有效范围: -1 到 INT_MAX (kB)
                if (intValue == -1) {
                    return "1048576";  // 1GB
                } else if (intValue > 0) {
                    return "-1";  // 禁用限制
                } else {
                    return "1048576";
                }
            }
            
            // 默认处理（按值的大小顺序）
            if (intValue == -1) {
                // -1通常表示禁用，改为一个小的正数进行测试
                return "2";
            } else if (intValue == 0) {
                // 对于0值，需要根据参数名称判断可能的范围
                if (name.contains("debug") || name.contains("trace") || 
                    name.contains("log_") || name.contains("_level")) {
                    // 调试、跟踪、日志级别参数，通常范围很小
                    return "1";
                } else if (name.contains("timeout") || name.contains("_time")) {
                    // 超时和时间参数，使用毫秒或秒
                    return "1000";
                } else if (name.contains("_size") || name.contains("_mem") || 
                           name.contains("_limit") || name.contains("_buffers")) {
                    // 大小、内存、限制参数，使用较大值
                    return "1000";
                } else {
                    // 其他参数，使用保守的小值
                    return "10";
                }
            } else if (intValue > 0 && intValue <= 10) {
                // 对于小值参数（可能有严格上限），使用加减法而不是乘法
                return intValue >= 5 ? String.valueOf(intValue - 2) : String.valueOf(intValue + 2);
            } else if (intValue > 10 && intValue <= 100) {
                // 对于中等值，使用加法而不是乘法
                return String.valueOf(intValue + 10);
            } else if (intValue > 100 && intValue <= 10000) {
                // 对于较大值，使用加法
                return String.valueOf(intValue + 100);
            } else if (intValue > 10000 && intValue <= 100000) {
                // 对于很大的值，使用加法
                return String.valueOf(intValue + 1000);
            } else if (intValue > 100000 && intValue < Integer.MAX_VALUE / 2) {
                // 对于非常大的值，使用加法，确保不会溢出
                return String.valueOf(intValue + 10000);
            } else {
                // 对于接近 INT_MAX 的值，减少而不是增加，避免溢出
                return String.valueOf(Math.max(intValue / 2, 0));
            }
        } catch (NumberFormatException e) {
            // 不是整数，继续尝试其他类型
        }
        
        // 浮点数参数 - 不需要引号
        try {
            double doubleValue = Double.parseDouble(currentValue);
            
            // 优先使用数据库提供的范围信息
            if (minVal != null && maxVal != null && !minVal.isEmpty() && !maxVal.isEmpty()) {
                try {
                    double min = Double.parseDouble(minVal);
                    double max = Double.parseDouble(maxVal);
                    
                    // 在有效范围内生成一个不同的值
                    if (Math.abs(doubleValue - min) < 0.0001) {
                        // 当前接近最小值
                        return String.valueOf(Math.min(min + (max - min) * 0.25, max));
                    } else if (Math.abs(doubleValue - max) < 0.0001) {
                        // 当前接近最大值
                        return String.valueOf(Math.max(max - (max - min) * 0.25, min));
                    } else if (doubleValue < (min + max) / 2) {
                        // 当前值在下半部分
                        return String.valueOf(Math.min(doubleValue + (max - min) * 0.25, max));
                    } else {
                        // 当前值在上半部分
                        return String.valueOf(Math.max(doubleValue - (max - min) * 0.25, min));
                    }
                } catch (NumberFormatException e) {
                    // min/max不是浮点数，继续使用原来的逻辑
                }
            }
            
            // 特殊处理：成本参数和分数参数
            if (name.contains("_cost") || name.contains("_fraction")) {
                // 成本参数和分数参数：使用加法而不是乘法，避免超出范围
                if (doubleValue < 1.0) {
                    // 对于小于1的值，加上一个小值
                    return String.valueOf(doubleValue + 0.01);
                } else {
                    // 对于大于等于1的值，加上一个适当的值
                    return String.valueOf(doubleValue + 1.0);
                }
            }
            
            // 默认处理
            if (doubleValue == 0) {
                return "1.0";
            } else {
                return String.valueOf(doubleValue * 1.5);
            }
        } catch (NumberFormatException e) {
            // 不是浮点数，继续尝试其他类型
        }
        
        // 字符串类型参数 - 根据参数名称特殊处理，使用有效值（需要带引号）
        if (name.contains("DateStyle")) {
            return "'" + (currentValue.contains("MDY") ? "ISO, DMY" : "ISO, MDY") + "'";
        } else if (name.contains("IntervalStyle")) {
            // IntervalStyle的有效值：postgres, postgres_verbose, sql_standard, iso_8601
            return "'" + (currentValue.equals("postgres") ? "sql_standard" : "postgres") + "'";
        } else if (name.contains("TimeZone") || name.equals("log_timezone")) {
            // 时区值需要引号
            return "'" + (currentValue.equals("UTC") ? "PRC" : "UTC") + "'";
        } else if (name.startsWith("lc_")) {
            // locale相关参数，只使用C locale，避免en_US.UTF-8可能不存在
            // 保持原值不变，避免设置无效的locale
            return "'" + currentValue + "'";
        } else if (name.equals("default_text_search_config")) {
            return "'" + (currentValue.contains("simple") ? "pg_catalog.english" : "pg_catalog.simple") + "'";
        } else if (name.equals("search_path")) {
            // search_path需要引号，注意值中不能包含双引号
            return "'" + (currentValue.contains("public") ? "pg_catalog, public" : "public, pg_catalog") + "'";
        } else if (name.equals("bytea_output")) {
            // 有效值：hex, escape
            return "'" + (currentValue.equals("hex") ? "escape" : "hex") + "'";
        } else if (name.equals("xmlbinary")) {
            // 有效值：base64, hex
            return "'" + (currentValue.equals("base64") ? "hex" : "base64") + "'";
        } else if (name.equals("xmloption")) {
            // 有效值：content, document
            return "'" + (currentValue.equals("content") ? "document" : "content") + "'";
        } else if (name.equals("constraint_exclusion")) {
            // 有效值：on, off, partition
            return "'" + (currentValue.equals("partition") ? "on" : "partition") + "'";
        } else if (name.equals("default_transaction_isolation")) {
            // 有效值：read uncommitted, read committed, repeatable read, serializable
            return "'" + (currentValue.contains("read committed") ? "serializable" : "read committed") + "'";
        } else if (name.equals("session_replication_role")) {
            // 有效值：origin, replica, local
            return "'" + (currentValue.equals("origin") ? "replica" : "origin") + "'";
        } else if (name.equals("backslash_quote")) {
            // 有效值：safe_encoding, on, off
            return "'" + (currentValue.equals("safe_encoding") ? "on" : "safe_encoding") + "'";
        } else if (name.equals("log_min_messages") || name.equals("client_min_messages")) {
            // 有效值：debug5, debug4, debug3, debug2, debug1, info, notice, warning, error, log, fatal, panic
            return "'" + (currentValue.equals("warning") ? "notice" : "warning") + "'";
        } else if (name.equals("log_min_error_statement")) {
            // 有效值：debug5, debug4, debug3, debug2, debug1, info, notice, warning, error, log, fatal, panic
            return "'" + (currentValue.equals("error") ? "warning" : "error") + "'";
        } else if (name.equals("log_statement")) {
            // 有效值：none, ddl, mod, all
            return "'" + (currentValue.equals("none") ? "all" : "none") + "'";
        } else if (name.equals("log_error_verbosity")) {
            // 有效值：terse, default, verbose
            return "'" + (currentValue.equals("default") ? "verbose" : "default") + "'";
        } else if (name.equals("trace_recovery_messages")) {
            // 有效值：debug5, debug4, debug3, debug2, debug1, log, notice, warning, error
            return "'" + (currentValue.equals("log") ? "notice" : "log") + "'";
        } else if (name.equals("application_name")) {
            // application_name可以是任意字符串
            return "'" + (currentValue.isEmpty() ? "test_app" : currentValue + "_modified") + "'";
        } else if (name.equals("default_tablespace")) {
            // 保持原值，避免设置不存在的表空间
            return "'" + (currentValue.isEmpty() ? "" : currentValue) + "'";
        }
        
        // 对于其他字符串参数，保持原值不变（避免设置无效值）
        // 但需要加引号（如果还没有引号的话）
        if (currentValue.startsWith("'") && currentValue.endsWith("'")) {
            // 已经有引号，直接返回
            return currentValue;
        } else {
            // 没有引号，添加引号
            return "'" + currentValue + "'";
        }
    }
    
    /**
     * 获取静态定义的100个GUC参数（手工编写，可能不准确）
     * Key: 参数名
     * Value: [默认值, 测试值]
     */
    public static Map<String, String[]> getGucParameters() {
        Map<String, String[]> params = new LinkedHashMap<>();
        
        // 1-10: 数值类型参数
        params.put("extra_float_digits", new String[]{"1", "3"});
        params.put("work_mem", new String[]{"4096", "8192"});
        params.put("maintenance_work_mem", new String[]{"65536", "131072"});
        // 注意：以下超时参数已移除，避免测试时连接被断开
        // - statement_timeout: 会导致语句超时
        // - lock_timeout: 会导致锁超时
        // - idle_in_transaction_session_timeout: 会导致事务内超时断开连接
        // - idle_session_timeout: 会导致会话超时断开连接
        params.put("temp_file_limit", new String[]{"-1", "1048576"});
        params.put("vacuum_cost_delay", new String[]{"0", "10"});
        params.put("vacuum_cost_limit", new String[]{"200", "400"});
        params.put("vacuum_cost_page_hit", new String[]{"1", "2"});
        
        // 11-16: 字符串类型参数（需要引号）
        // 注意：以下JDBC驱动敏感参数已移除，不允许修改：
        // - client_encoding: JDBC驱动强制要求UTF8
        // - DateStyle: JDBC驱动要求以ISO开头
        // - TimeZone: JDBC驱动敏感参数
        // - IntervalStyle: JDBC驱动敏感参数
        
        // lc_*参数：保持C locale，避免使用可能不存在的en_US.UTF-8
        params.put("lc_messages", new String[]{"'C'", "'C'"});  // 保持不变，避免设置无效locale
        params.put("lc_monetary", new String[]{"'C'", "'C'"});  // 保持不变
        params.put("lc_numeric", new String[]{"'C'", "'C'"});   // 保持不变
        params.put("lc_time", new String[]{"'C'", "'C'"});      // 保持不变
        params.put("default_text_search_config", new String[]{"'pg_catalog.simple'", "'pg_catalog.english'"});
        params.put("search_path", new String[]{"'public'", "'pg_catalog, public'"});  // 移除有问题的$user
        
        // 21-30: 布尔类型参数
        params.put("standard_conforming_strings", new String[]{"on", "off"});
        params.put("escape_string_warning", new String[]{"on", "off"});
        params.put("synchronize_seqscans", new String[]{"on", "off"});
        params.put("enable_seqscan", new String[]{"on", "off"});
        params.put("enable_indexscan", new String[]{"on", "off"});
        params.put("enable_bitmapscan", new String[]{"on", "off"});
        params.put("enable_tidscan", new String[]{"on", "off"});
        params.put("enable_sort", new String[]{"on", "off"});
        params.put("enable_hashjoin", new String[]{"on", "off"});
        params.put("enable_mergejoin", new String[]{"on", "off"});
        
        // 31-40: 更多布尔参数
        params.put("enable_nestloop", new String[]{"on", "off"});
        params.put("enable_material", new String[]{"on", "off"});
        params.put("enable_hashagg", new String[]{"on", "off"});
        params.put("enable_parallel_append", new String[]{"on", "off"});
        params.put("enable_parallel_hash", new String[]{"on", "off"});
        params.put("enable_partition_pruning", new String[]{"on", "off"});
        params.put("enable_partitionwise_join", new String[]{"off", "on"});
        params.put("enable_partitionwise_aggregate", new String[]{"off", "on"});
        params.put("enable_gathermerge", new String[]{"on", "off"});
        params.put("constraint_exclusion", new String[]{"'partition'", "'on'"});  // 需要引号
        
        // 41-50: 成本参数（浮点数不需要引号）
        params.put("seq_page_cost", new String[]{"1.0", "2.0"});
        params.put("random_page_cost", new String[]{"4.0", "2.0"});
        params.put("cpu_tuple_cost", new String[]{"0.01", "0.02"});
        params.put("cpu_index_tuple_cost", new String[]{"0.005", "0.01"});
        params.put("cpu_operator_cost", new String[]{"0.0025", "0.005"});
        params.put("parallel_tuple_cost", new String[]{"0.1", "0.2"});
        params.put("parallel_setup_cost", new String[]{"1000.0", "2000.0"});
        params.put("min_parallel_table_scan_size", new String[]{"8388608", "4194304"});
        params.put("min_parallel_index_scan_size", new String[]{"524288", "262144"});
        params.put("effective_cache_size", new String[]{"524288", "1048576"});
        
        // 51-60: 规划器参数
        params.put("default_statistics_target", new String[]{"100", "200"});
        params.put("from_collapse_limit", new String[]{"8", "16"});
        params.put("join_collapse_limit", new String[]{"8", "16"});
        params.put("cursor_tuple_fraction", new String[]{"0.1", "0.5"});  // 范围: 0.0-1.0
        params.put("geqo_threshold", new String[]{"12", "10"});
        params.put("geqo_effort", new String[]{"5", "3"});  // 范围: 1-10
        params.put("geqo_pool_size", new String[]{"0", "100"});
        params.put("geqo_generations", new String[]{"0", "100"});
        params.put("geqo_selection_bias", new String[]{"2.0", "1.5"});  // 范围: 1.5-2.0，不能超过！
        params.put("geqo_seed", new String[]{"0.0", "0.5"});  // 范围: 0.0-1.0
        
        // 61-70: 日志参数
        params.put("log_min_messages", new String[]{"'warning'", "'notice'"});  // 需要引号
        params.put("log_min_error_statement", new String[]{"'error'", "'warning'"});  // 需要引号
        params.put("log_min_duration_statement", new String[]{"-1", "1000"});
        params.put("log_statement", new String[]{"'none'", "'all'"});  // 需要引号
        params.put("log_duration", new String[]{"off", "on"});
        params.put("log_error_verbosity", new String[]{"'default'", "'verbose'"});  // 需要引号
        params.put("log_lock_waits", new String[]{"off", "on"});
        params.put("log_temp_files", new String[]{"-1", "0"});
        params.put("log_timezone", new String[]{"'UTC'", "'PRC'"});  // 需要引号
        params.put("application_name", new String[]{"''", "'test_app'"});  // 需要引号，即使是空字符串
        
        // 71-79: 客户端连接参数
        params.put("deadlock_timeout", new String[]{"1000", "2000"});
        params.put("max_locks_per_transaction", new String[]{"64", "128"});
        params.put("max_pred_locks_per_transaction", new String[]{"64", "128"});
        params.put("array_nulls", new String[]{"on", "off"});
        params.put("backslash_quote", new String[]{"'safe_encoding'", "'on'"});  // 需要引号
        // 注意：default_tablespace已移除，可能不存在的表空间会导致错误
        params.put("default_transaction_isolation", new String[]{"'read committed'", "'serializable'"});  // 需要引号
        params.put("default_transaction_read_only", new String[]{"off", "on"});
        params.put("default_transaction_deferrable", new String[]{"off", "on"});
        params.put("session_replication_role", new String[]{"'origin'", "'replica'"});  // 需要引号
        
        // 81-90: 其他参数
        params.put("bytea_output", new String[]{"'hex'", "'escape'"});  // 需要引号
        params.put("xmlbinary", new String[]{"'base64'", "'hex'"});  // 需要引号
        params.put("xmloption", new String[]{"'content'", "'document'"});  // 需要引号
        params.put("gin_fuzzy_search_limit", new String[]{"0", "1000"});
        params.put("gin_pending_list_limit", new String[]{"4096", "8192"});
        params.put("vacuum_freeze_min_age", new String[]{"50000000", "100000000"});
        params.put("vacuum_freeze_table_age", new String[]{"150000000", "200000000"});
        params.put("vacuum_multixact_freeze_min_age", new String[]{"5000000", "10000000"});
        params.put("vacuum_multixact_freeze_table_age", new String[]{"150000000", "200000000"});
        params.put("transform_null_equals", new String[]{"off", "on"});
        
        // 86-93: 更多参数
        params.put("quote_all_identifiers", new String[]{"off", "on"});
        params.put("row_security", new String[]{"on", "off"});
        params.put("check_function_bodies", new String[]{"on", "off"});
        // 注意：default_with_oids已移除（PostgreSQL 12+已废弃）
        params.put("lo_compat_privileges", new String[]{"off", "on"});
        params.put("operator_precedence_warning", new String[]{"off", "on"});
        params.put("trace_notify", new String[]{"off", "on"});
        params.put("trace_recovery_messages", new String[]{"'log'", "'notice'"});  // 需要引号
        params.put("trace_sort", new String[]{"off", "on"});
        // 注意：总计94个参数（移除了client_encoding, DateStyle, TimeZone, IntervalStyle, default_tablespace, default_with_oids）
        
        return params;
    }
    
    /**
     * 获取参数数量
     */
    public static int getParameterCount() {
        return getGucParameters().size();
    }
}
