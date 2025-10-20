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
        
        String sql = "SELECT name, setting, unit, context " +
                     "FROM pg_settings " +
                     "WHERE context IN ('user', 'superuser') " +  // 只获取用户可设置的参数
                     "AND name NOT LIKE 'pg_%' " +  // 排除内部参数
                     "ORDER BY name " +
                     "LIMIT " + limit;
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String name = rs.getString("name");
                String currentValue = rs.getString("setting");
                String unit = rs.getString("unit");
                
                // 为每个参数生成一个测试值（与当前值不同）
                String testValue = generateTestValue(name, currentValue, unit);
                
                params.put(name, new String[]{currentValue, testValue});
            }
        }
        
        return params;
    }
    
    /**
     * 根据参数名称和当前值生成一个不同的测试值
     */
    private static String generateTestValue(String name, String currentValue, String unit) {
        // 处理空字符串的情况 - 统一返回一个非空测试值
        if (currentValue == null || currentValue.isEmpty()) {
            return "'test_value'";
        }
        
        // 布尔类型参数
        if ("on".equalsIgnoreCase(currentValue)) {
            return "off";
        } else if ("off".equalsIgnoreCase(currentValue)) {
            return "on";
        }
        
        // 数值类型参数
        try {
            int intValue = Integer.parseInt(currentValue);
            if (intValue == 0) {
                return "1000";
            } else {
                return String.valueOf(intValue * 2);
            }
        } catch (NumberFormatException e) {
            // 不是整数，继续尝试其他类型
        }
        
        // 字符串类型参数 - 根据参数名称特殊处理，使用有效值（带引号）
        if (name.contains("DateStyle")) {
            return "'" + (currentValue.contains("MDY") ? "ISO, DMY" : "ISO, MDY") + "'";
        } else if (name.contains("IntervalStyle")) {
            return "'" + (currentValue.equals("postgres") ? "sql_standard" : "postgres") + "'";
        } else if (name.contains("TimeZone") || name.equals("log_timezone")) {
            return "'" + (currentValue.equals("UTC") ? "Asia/Shanghai" : "UTC") + "'";
        } else if (name.equals("client_encoding")) {
            return "'" + (currentValue.equals("UTF8") ? "SQL_ASCII" : "UTF8") + "'";
        } else if (name.startsWith("lc_")) {
            // locale相关参数，只在C和en_US.UTF-8之间切换
            return "'" + (currentValue.equals("C") ? "en_US.UTF-8" : "C") + "'";
        } else if (name.equals("default_text_search_config")) {
            return "'" + (currentValue.contains("simple") ? "pg_catalog.english" : "pg_catalog.simple") + "'";
        } else if (name.equals("search_path")) {
            return "'" + (currentValue.contains("public") ? "pg_catalog, public" : "public, pg_catalog") + "'";
        } else if (name.equals("bytea_output")) {
            return "'" + (currentValue.equals("hex") ? "escape" : "hex") + "'";
        } else if (name.equals("xmlbinary")) {
            return "'" + (currentValue.equals("base64") ? "hex" : "base64") + "'";
        } else if (name.equals("xmloption")) {
            return "'" + (currentValue.equals("content") ? "document" : "content") + "'";
        } else if (name.equals("constraint_exclusion")) {
            return "'" + (currentValue.equals("partition") ? "on" : "partition") + "'";
        } else if (name.equals("default_transaction_isolation")) {
            return "'" + (currentValue.contains("read committed") ? "serializable" : "read committed") + "'";
        } else if (name.equals("session_replication_role")) {
            return "'" + (currentValue.equals("origin") ? "replica" : "origin") + "'";
        } else if (name.equals("backslash_quote")) {
            return "'" + (currentValue.equals("safe_encoding") ? "on" : "safe_encoding") + "'";
        } else if (name.equals("log_min_messages") || name.equals("client_min_messages")) {
            return "'" + (currentValue.equals("warning") ? "notice" : "warning") + "'";
        } else if (name.equals("log_min_error_statement")) {
            return "'" + (currentValue.equals("error") ? "warning" : "error") + "'";
        } else if (name.equals("log_statement")) {
            return "'" + (currentValue.equals("none") ? "all" : "none") + "'";
        } else if (name.equals("log_error_verbosity")) {
            return "'" + (currentValue.equals("default") ? "verbose" : "default") + "'";
        } else if (name.equals("trace_recovery_messages")) {
            return "'" + (currentValue.equals("log") ? "notice" : "log") + "'";
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
        params.put("statement_timeout", new String[]{"0", "30000"});
        params.put("lock_timeout", new String[]{"0", "5000"});
        params.put("idle_in_transaction_session_timeout", new String[]{"0", "60000"});
        params.put("temp_file_limit", new String[]{"-1", "1048576"});
        params.put("vacuum_cost_delay", new String[]{"0", "10"});
        params.put("vacuum_cost_limit", new String[]{"200", "400"});
        params.put("vacuum_cost_page_hit", new String[]{"1", "2"});
        
        // 11-20: 字符串类型参数
        params.put("DateStyle", new String[]{"ISO, MDY", "ISO, DMY"});
        params.put("IntervalStyle", new String[]{"postgres", "sql_standard"});
        params.put("TimeZone", new String[]{"UTC", "Asia/Shanghai"});
        params.put("client_encoding", new String[]{"UTF8", "SQL_ASCII"});
        params.put("lc_messages", new String[]{"C", "en_US.UTF-8"});
        params.put("lc_monetary", new String[]{"C", "en_US.UTF-8"});
        params.put("lc_numeric", new String[]{"C", "en_US.UTF-8"});
        params.put("lc_time", new String[]{"C", "en_US.UTF-8"});
        params.put("default_text_search_config", new String[]{"pg_catalog.simple", "pg_catalog.english"});
        params.put("search_path", new String[]{"\"$user\", public", "public, pg_catalog"});
        
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
        params.put("constraint_exclusion", new String[]{"partition", "on"});
        
        // 41-50: 成本参数
        params.put("seq_page_cost", new String[]{"1", "2"});
        params.put("random_page_cost", new String[]{"4", "2"});
        params.put("cpu_tuple_cost", new String[]{"0.01", "0.02"});
        params.put("cpu_index_tuple_cost", new String[]{"0.005", "0.01"});
        params.put("cpu_operator_cost", new String[]{"0.0025", "0.005"});
        params.put("parallel_tuple_cost", new String[]{"0.1", "0.2"});
        params.put("parallel_setup_cost", new String[]{"1000", "2000"});
        params.put("min_parallel_table_scan_size", new String[]{"8388608", "4194304"});
        params.put("min_parallel_index_scan_size", new String[]{"524288", "262144"});
        params.put("effective_cache_size", new String[]{"524288", "1048576"});
        
        // 51-60: 规划器参数
        params.put("default_statistics_target", new String[]{"100", "200"});
        params.put("from_collapse_limit", new String[]{"8", "16"});
        params.put("join_collapse_limit", new String[]{"8", "16"});
        params.put("cursor_tuple_fraction", new String[]{"0.1", "0.2"});
        params.put("geqo_threshold", new String[]{"12", "10"});
        params.put("geqo_effort", new String[]{"5", "7"});
        params.put("geqo_pool_size", new String[]{"0", "100"});
        params.put("geqo_generations", new String[]{"0", "100"});
        params.put("geqo_selection_bias", new String[]{"2", "1.5"});
        params.put("geqo_seed", new String[]{"0", "0.5"});
        
        // 61-70: 日志参数
        params.put("log_min_messages", new String[]{"warning", "notice"});
        params.put("log_min_error_statement", new String[]{"error", "warning"});
        params.put("log_min_duration_statement", new String[]{"-1", "1000"});
        params.put("log_statement", new String[]{"none", "all"});
        params.put("log_duration", new String[]{"off", "on"});
        params.put("log_error_verbosity", new String[]{"default", "verbose"});
        params.put("log_lock_waits", new String[]{"off", "on"});
        params.put("log_temp_files", new String[]{"-1", "0"});
        params.put("log_timezone", new String[]{"UTC", "Asia/Shanghai"});
        params.put("application_name", new String[]{"", "test_app"});
        
        // 71-80: 客户端连接参数
        params.put("deadlock_timeout", new String[]{"1000", "2000"});
        params.put("max_locks_per_transaction", new String[]{"64", "128"});
        params.put("max_pred_locks_per_transaction", new String[]{"64", "128"});
        params.put("array_nulls", new String[]{"on", "off"});
        params.put("backslash_quote", new String[]{"safe_encoding", "on"});
        params.put("default_tablespace", new String[]{"", "pg_default"});
        params.put("default_transaction_isolation", new String[]{"read committed", "serializable"});
        params.put("default_transaction_read_only", new String[]{"off", "on"});
        params.put("default_transaction_deferrable", new String[]{"off", "on"});
        params.put("session_replication_role", new String[]{"origin", "replica"});
        
        // 81-90: 其他参数
        params.put("bytea_output", new String[]{"hex", "escape"});
        params.put("xmlbinary", new String[]{"base64", "hex"});
        params.put("xmloption", new String[]{"content", "document"});
        params.put("gin_fuzzy_search_limit", new String[]{"0", "1000"});
        params.put("gin_pending_list_limit", new String[]{"4096", "8192"});
        params.put("vacuum_freeze_min_age", new String[]{"50000000", "100000000"});
        params.put("vacuum_freeze_table_age", new String[]{"150000000", "200000000"});
        params.put("vacuum_multixact_freeze_min_age", new String[]{"5000000", "10000000"});
        params.put("vacuum_multixact_freeze_table_age", new String[]{"150000000", "200000000"});
        params.put("transform_null_equals", new String[]{"off", "on"});
        
        // 91-100: 更多参数
        params.put("quote_all_identifiers", new String[]{"off", "on"});
        params.put("row_security", new String[]{"on", "off"});
        params.put("check_function_bodies", new String[]{"on", "off"});
        params.put("default_with_oids", new String[]{"off", "on"});
        params.put("lo_compat_privileges", new String[]{"off", "on"});
        params.put("operator_precedence_warning", new String[]{"off", "on"});
        params.put("trace_notify", new String[]{"off", "on"});
        params.put("trace_recovery_messages", new String[]{"log", "notice"});
        params.put("trace_sort", new String[]{"off", "on"});
        params.put("zero_damaged_pages", new String[]{"off", "on"});
        
        return params;
    }
    
    /**
     * 获取参数数量
     */
    public static int getParameterCount() {
        return getGucParameters().size();
    }
}
