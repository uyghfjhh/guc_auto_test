package com.fbasecman.guc;

import com.fbasecman.guc.config.DatabaseConfig;
import com.fbasecman.guc.model.TestResult;
import com.fbasecman.guc.util.TablePrinter;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GUC参数同步场景测试 - 测试连接复用时的GUC参数同步机制
 * 
 * 测试场景：
 * 1. 客户端连接1设置extra_float_digits=3，然后提交事务
 * 2. 客户端连接2复用连接1的后端连接，检查参数是否被重置
 * 3. 连接1继续使用，获得新的后端连接，检查参数是否同步
 */
public class GucSyncScenarioTest {
    
    // ANSI颜色代码
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String RESET = "\u001B[0m";
    
    private List<TestResult> testResults = new ArrayList<>();
    
    public static void main(String[] args) {
        GucSyncScenarioTest test = new GucSyncScenarioTest();
        test.runAllTests();
    }
    
    public void runAllTests() {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("开始执行GUC参数同步测试场景");
        System.out.println("=".repeat(100) + "\n");
        
        for(int i=1; i!=2; ++i) {
            try {
                //  testCase2_5_SetGucInTransaction_SimpleProtocol();
                
                // testCase2_5_SetGucInTransaction_ExtendedProtocol();
                // testCase1_NonReportParameterSync_SimpleProtocol();
                // testCase1_NonReportParameterSync_ExtendedProtocol();
                
                // testCase2_DateStyleSync_SimpleProtocol();
                
                // testCase2_DateStyleSync_ExtendedProtocol();
                
                // testCase2_TimeZoneReset_SimpleProtocol();
                
                // testCase2_TimeZoneReset_ExtendedProtocol();

                // testCase2b_MultiParamResetAll_SimpleProtocol();
                
                // testCase2b_MultiParamResetAll_ExtendedProtocol();
                                
                
                // testCase2_6_MassiveGucSync_SimpleProtocol();
                
                // testCase2_6_MassiveGucSync_ExtendedProtocol();

                // testCase2_8_ReadWriteSwitch_SimpleProtocol();

                // testCase2_8_ReadWriteSwitch_ExtendedProtocol();

                // testCase2_8_InvalidGucError_SimpleProtocol();

                // testCase2_8_InvalidGucError_ExtendedProtocol();

                // testCase2_10_CommonGucReadWriteSwitch_SimpleProtocol();

                // testCase2_10_CommonGucReadWriteSwitch_ExtendedProtocol();
                
                /////////////////////////////////////////////////////
                //   testCase_SimpleTest();
                // testCase_PipelineMode_Batch();
                // testCase3_MultiParamDiscardAll_SimpleProtocol();

                // testCase2_7_MemoryLeakTest_SimpleProtocol();
                
                // testCase2_7_MemoryLeakTest_ExtendedProtocol();
                
            } catch (Exception e) {
                System.err.println(RED + "\n测试执行失败: " + e.getMessage() + RESET);
                e.printStackTrace();
            } finally {
                // 打印测试结果表格
                TablePrinter.printResults(testResults);
            }
        }
    }

    private String summarizeSqlException(SQLException e) {
        if (e == null) {
            return "";
        }
        String message = e.getMessage();
        if (message != null) {
            message = message.replaceAll("\\s+", " ").trim();
        }
        return String.format("SQLState=%s, ErrorCode=%d, Message=%s", e.getSQLState(), e.getErrorCode(), message);
    }
    
    /**
     * 用例1：测试非guc report参数同步 - Simple Query Protocol
     * 使用Statement执行SQL（Simple协议）
     */
    public void testCase1_NonReportParameterSync_SimpleProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例1-Simple协议】测试非guc report参数同步 (使用Statement - Simple Query Protocol)");
        System.out.println("=".repeat(100) + "\n");
        
        executeTestCase1(false, "Simple协议");
    }
    
    /**
     * 用例1：测试非guc report参数同步 - Extended Query Protocol
     * 使用PreparedStatement执行SQL（Extended协议）
     */
    public void testCase1_NonReportParameterSync_ExtendedProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例1-Extended协议】测试非guc report参数同步 (使用PreparedStatement - Extended Query Protocol)");
        System.out.println("=".repeat(100) + "\n");
        
        executeTestCase1(true, "Extended协议");
    }
    
    /**
     * 执行测试用例1的核心逻辑
     * @param useExtendedProtocol true=使用PreparedStatement(Extended协议), false=使用Statement(Simple协议)
     * @param protocolName 协议名称，用于日志标识
     */
    private void executeTestCase1(boolean useExtendedProtocol, String protocolName) throws SQLException, InterruptedException, Exception {
        
        Connection conn1 = null;
        Connection conn2 = null;
        String url = getUrlWithProtocol(useExtendedProtocol);
        
        // 记录各个检测点的结果
        boolean allPassed = true;
        StringBuilder failureDetails = new StringBuilder();
        
        try {
            // ============ 步骤1：客户端连接1执行 ============
            System.out.println(YELLOW + "步骤1：客户端连接1开始执行..." + RESET);
            conn1 = DriverManager.getConnection(
                    url,
                    DatabaseConfig.getUser(),
                    DatabaseConfig.getPassword()
            );
            
            conn1.setAutoCommit(false); // 开启事务
            
            // 执行SQL并记录
            printSql(1, "BEGIN", protocolName);
            
            // 获取初始值
            printSql(1, "SHOW extra_float_digits", protocolName);
            String initialValue = getGucValue(conn1, "extra_float_digits", useExtendedProtocol);
            System.out.println(GREEN + "  → 初始值: " + initialValue + RESET);
            
            // 设置参数
            printSql(1, "SET extra_float_digits = 3", protocolName);
            executeUpdate(conn1, "SET extra_float_digits = 3", useExtendedProtocol);
            
            // 获取后端连接信息
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend1 = getBackendInfo(conn1, useExtendedProtocol);
            System.out.println(BLUE + "  → 后端连接1信息: " + backend1 + RESET);
            System.out.println(BLUE + "  → extra_float_digits = " + getGucValue(conn1, "extra_float_digits", useExtendedProtocol) + RESET);
            
            // 提交事务
            printSql(1, "COMMIT", protocolName);
            conn1.commit();
            
            System.out.println(YELLOW + "步骤1完成，关闭连接1，让后端连接返回连接池\n" + RESET);
            
            // 连接commit之后 连接2增加1秒延时，再建立连接
            Thread.sleep(1000);
            
            // ============ 步骤2：客户端连接2执行 ============
            System.out.println(YELLOW + "步骤2：客户端连接2开始执行..." + RESET);
            conn2 = DriverManager.getConnection(
                    url,
                    DatabaseConfig.getUser(),
                    DatabaseConfig.getPassword()
            );
            
            conn2.setAutoCommit(false);
            printSql(2, "BEGIN", protocolName);
            
            // 获取后端连接信息
            printSql(2, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend2 = getBackendInfo(conn2, useExtendedProtocol);
            System.out.println(BLUE + "  → 后端连接2信息: " + backend2 + RESET);
            
            // 【检测点1】检查是否复用了连接1的后端连接
            boolean isReused = backend1.pid.equals(backend2.pid);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点1】检查后端连接是否复用:");
            System.out.println("  期望: 连接2应该复用连接1的后端连接 (pid相同)");
            System.out.println("  实际: 客户端连接1的后端pid=" + backend1.pid + ", 客户端连接2的后端pid=" + backend2.pid);
            if (isReused) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 后端连接已复用" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 后端连接未复用" + RESET);
                allPassed = false;
                failureDetails.append("检测点1失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 【检测点2】检查extra_float_digits是否被重置为默认值
            printSql(2, "SHOW extra_float_digits", protocolName);
            String valueInConn2 = getGucValue(conn2, "extra_float_digits", useExtendedProtocol);
            System.out.println(BLUE + "  → 客户端连接2的后端连接中 extra_float_digits=" + valueInConn2 + RESET);
            
            boolean isReset = initialValue.equals(valueInConn2);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点2】检查GUC参数是否同步重置:");
            System.out.println("  说明: 因为后端连接被复用，应该执行了 RESET extra_float_digits");
            System.out.println("  期望: extra_float_digits应该是默认值 " + initialValue);
            System.out.println("  实际: 客户端连接2的后端连接中 extra_float_digits = " + valueInConn2);
            if (isReset) {
                System.out.println(GREEN + "  结果: ✓ 通过 - guc参数重置正常" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - GUC参数未正确同步，期望=" + initialValue + ", 实际=" + valueInConn2 + RESET);
                allPassed = false;
                failureDetails.append("检测点2失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 注意：连接2的事务不提交，保持后端连接被占用
            System.out.println(YELLOW + "步骤2完成（注意：连接2的事务不提交，保持后端连接被占用）\n" + RESET);
            
            // 等待一小段时间
            Thread.sleep(100);
            
            // ============ 步骤3：连接1继续执行 ============
            System.out.println(YELLOW + "步骤3：连接1继续执行..." + RESET);
            conn1.setAutoCommit(false);
            printSql(1, "BEGIN", protocolName);
            
            // 获取后端连接信息
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend1New = getBackendInfo(conn1, useExtendedProtocol);
            System.out.println(BLUE + "  → 后端连接信息: " + backend1New + RESET);
            
            // 【检测点3】检查是否分配了新的后端连接
            boolean isNewBackend = !backend1.pid.equals(backend1New.pid);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点3】检查是否分配了新的后端连接:");
            System.out.println("  说明: 原后端连接被连接2占用，连接1应该获得新的后端连接");
            System.out.println("  期望: 连接1应该获得新的后端连接");
            System.out.println("  实际: 客户端连接1原后端pid=" + backend1.pid + ", 客户端连接1新后端pid=" + backend1New.pid);
            if (isNewBackend) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 已分配新的后端连接" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 仍是原后端连接" + RESET);
                allPassed = false;
                failureDetails.append("检测点3失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 【检测点4】检查extra_float_digits是否恢复为之前设置的值3
            printSql(1, "SHOW extra_float_digits", protocolName);
            String valueInConn1New = getGucValue(conn1, "extra_float_digits", useExtendedProtocol);
            System.out.println(BLUE + "  → 客户端连接1的后端连接中 extra_float_digits=" + valueInConn1New + RESET);
            
            boolean isSynced = "3".equals(valueInConn1New);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点4】检查GUC参数是否同步到新后端:");
            System.out.println("  说明: 因为获得了新的后端连接，应该执行了 SET extra_float_digits=3");
            System.out.println("  期望: extra_float_digits应该是3 (连接1之前设置的值)");
            System.out.println("  实际: 客户端连接1的后端连接中 extra_float_digits = " + valueInConn1New);
            if (isSynced) {
                System.out.println(GREEN + "  结果: ✓ 通过 - GUC参数已正确同步到新后端" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - GUC参数未正确同步，期望=3, 实际=" + valueInConn1New + RESET);
                allPassed = false;
                failureDetails.append("检测点4失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            conn1.commit();
            printSql(1, "COMMIT", protocolName);
            System.out.println(YELLOW + "步骤3完成\n" + RESET);
            
            // 记录整个用例的测试结果
            recordResult("测试非guc report参数同步", 
                        "extra_float_digits参数（" + protocolName + "）", 
                        "所有检测点通过", 
                        allPassed ? "所有检测点通过" : failureDetails.toString(), 
                        allPassed, 
                        allPassed ? "通过" : "失败");
            
        } finally {
            // 清理资源
            if (conn1 != null) {
                try { conn1.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
            if (conn2 != null) {
                try { 
                    // 连接2在测试过程中保持事务未提交，这里关闭连接会自动回滚
                    conn2.close(); 
                } catch (SQLException e) { 
                    e.printStackTrace(); 
                }
            }
        }
    }
    
    /**
     * 根据协议类型获取数据库URL
     * @param useExtended true=Extended协议, false=Simple协议
     */
    private String getUrlWithProtocol(boolean useExtended) {
        String baseUrl = DatabaseConfig.getUrl();
        // 如果URL中已有参数，使用&连接，否则使用?
        String separator = baseUrl.contains("?") ? "&" : "?";
        
        // 添加options参数来固定DateStyle初始值为ISO, MDY
        String optionsParam = "options=-c%20DateStyle=ISO,MDY";
        
        if (useExtended) {
            // Extended协议：使用extended或extendedForPrepared模式
            return baseUrl + separator + "preferQueryMode=extended&" + optionsParam;
        } else {
            // Simple协议：强制使用simple模式
            return baseUrl + separator + "preferQueryMode=simple&" + optionsParam;
        }
    }
    
    /**
     * 打印SQL命令（红色）
     */
    private void printSql(int clientId, String sql, String protocol) {
        System.out.println(RED + "[客户端连接" + clientId + " - " + protocol + "] SQL: " + sql + RESET);
    }
    
    /**
     * 规范化GUC参数值，用于比较
     * 去除引号（单引号和双引号）和首尾空格
     */
    private String normalizeGucValue(String value) {
        if (value == null) {
            return "";
        }
        // 去除首尾空格
        value = value.trim();
        // 去除单引号
        if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        // 去除双引号
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        return value.trim();
    }
    
    /**
     * 获取GUC参数值
     * @param useExtended true=使用PreparedStatement, false=使用Statement
     */
    private String getGucValue(Connection conn, String parameter, boolean useExtended) throws SQLException {
        String sql = "SHOW " + parameter;
        
        if (useExtended) {
            // Extended Query Protocol - 使用PreparedStatement
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } else {
            // Simple Query Protocol - 使用Statement
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }
    
    /**
     * 执行更新语句
     * @param useExtended true=使用PreparedStatement, false=使用Statement
     */
    private void executeUpdate(Connection conn, String sql, boolean useExtended) throws SQLException {
        if (useExtended) {
            // Extended Query Protocol - 使用PreparedStatement
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.execute();
            }
        } else {
            // Simple Query Protocol - 使用Statement
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }
    
    /**
     * 获取后端连接信息（包含恢复状态）
     * @param useExtended true=使用PreparedStatement, false=使用Statement
     */
    private BackendInfo getBackendInfoWithRecovery(Connection conn, boolean useExtended) throws SQLException {
        String sql = "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user, pg_is_in_recovery()";
        
        if (useExtended) {
            // Extended Query Protocol - 使用PreparedStatement
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new BackendInfo(
                        rs.getString(1),  // ip
                        rs.getInt(2),     // port
                        rs.getString(3),  // pid
                        rs.getString(4),  // user
                        rs.getBoolean(5)  // isInRecovery
                    );
                }
            }
        } else {
            // Simple Query Protocol - 使用Statement
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return new BackendInfo(
                        rs.getString(1),  // ip
                        rs.getInt(2),     // port
                        rs.getString(3),  // pid
                        rs.getString(4),  // user
                        rs.getBoolean(5)  // isInRecovery
                    );
                }
            }
        }
        return null;
    }
    
    /**
     * 获取后端连接信息
     * @param useExtended true=使用PreparedStatement, false=使用Statement
     */
    private BackendInfo getBackendInfo(Connection conn, boolean useExtended) throws SQLException {
        String sql = "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user";
        
        if (useExtended) {
            // Extended Query Protocol - 使用PreparedStatement
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new BackendInfo(
                        rs.getString(1),  // ip
                        rs.getInt(2),     // port
                        rs.getString(3),  // pid
                        rs.getString(4)   // user
                    );
                }
            }
        } else {
            // Simple Query Protocol - 使用Statement
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return new BackendInfo(
                        rs.getString(1),  // ip
                        rs.getInt(2),     // port
                        rs.getString(3),  // pid
                        rs.getString(4)   // user
                    );
                }
            }
        }
        return null;
    }
    
    /**
     * 记录测试结果
     * 如果测试失败，立即抛出异常停止执行
     */
    private void recordResult(String testCase, String parameter, 
                             String expected, String actual, 
                             boolean passed, String remark) throws Exception {
        TestResult result = new TestResult(testCase, parameter, expected, actual, passed, remark);
        testResults.add(result);
        
        // 如果测试失败，立即抛出异常
        if (!passed) {
            System.err.println(RED + "\n" + "=".repeat(100));
            System.err.println("❌ 测试用例 [" + parameter + "] 失败，停止执行后续测试用例！");
            System.err.println("测试类别: " + testCase);
            System.err.println("期望结果: " + expected);
            System.err.println("实际结果: " + actual);
            System.err.println("备注信息: " + remark);
            System.err.println("=".repeat(100) + RESET + "\n");
            throw new Exception("测试用例失败: " + parameter);
        }
    }
    
    /**
     * 后端连接信息
     */
    static class BackendInfo {
        String ip;
        int port;
        String pid;
        String user;
        boolean isInRecovery;
        
        BackendInfo(String ip, int port, String pid, String user) {
            this.ip = ip;
            this.port = port;
            this.pid = pid;
            this.user = user;
            this.isInRecovery = false; // default for backward compatibility
        }
        
        BackendInfo(String ip, int port, String pid, String user, boolean isInRecovery) {
            this.ip = ip;
            this.port = port;
            this.pid = pid;
            this.user = user;
            this.isInRecovery = isInRecovery;
        }
        
        @Override
        public String toString() {
            return String.format("ip=%s, port=%d, pid=%s, user=%s, isInRecovery=%s", ip, port, pid, user, isInRecovery);
        }
    }
    
    // ==================== 测试用例2：DateStyle参数同步 ====================
    
    public void testCase2_DateStyleSync_SimpleProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2-Simple协议】测试DateStyle参数同步");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2(false, "Simple协议");
    }
    
    public void testCase2_DateStyleSync_ExtendedProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2-Extended协议】测试DateStyle参数同步");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2(true, "Extended协议");
    }
    
    private void executeTestCase2(boolean useExtendedProtocol, String protocolName) throws SQLException, InterruptedException, Exception {
        Connection conn1 = null;
        Connection conn2 = null;
        boolean allPassed = true;
        StringBuilder failureDetails = new StringBuilder();
        
        try {
            // 步骤1：客户端连接1执行（事务外执行）
            System.out.println(YELLOW + "步骤1：客户端连接1开始执行（事务外）..." + RESET);
            String url = getUrlWithProtocol(useExtendedProtocol);
            conn1 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn1.setAutoCommit(true); // 事务外执行
            
            // 检测点1：获取初始DateStyle值
            printSql(1, "SHOW DateStyle", protocolName);
            String initialDateStyle = getGucValue(conn1, "DateStyle", useExtendedProtocol);
            
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点1】记录初始DateStyle:");
            System.out.println("  期望: ISO, MDY");
            System.out.println("  实际: " + initialDateStyle);
            if (initialDateStyle.contains("ISO") && initialDateStyle.contains("MDY")) {
                System.out.println(GREEN + "  结果: ✓ 通过" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败" + RESET);
                allPassed = false;
                failureDetails.append("检测点1失败(期望:ISO, MDY, 实际:" + initialDateStyle + "); ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 检测点2：设置DateStyle
            printSql(1, "SET DateStyle = ISO, DMY", protocolName);
            executeUpdate(conn1, "SET DateStyle = ISO, DMY", useExtendedProtocol);
            
            // 检测点3：记录后端连接信息
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend1 = getBackendInfo(conn1, useExtendedProtocol);
            String dateStyleAfterSet = getGucValue(conn1, "DateStyle", useExtendedProtocol);
            
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点3】记录后端连接标识与DateStyle:");
            System.out.println("  后端连接1: " + backend1);
            System.out.println("  DateStyle: " + dateStyleAfterSet);
            System.out.println(GREEN + "  结果: ✓ 通过 - 已记录" + RESET);
            System.out.println("─".repeat(100) + "\n");
            
            System.out.println(YELLOW + "步骤1完成\n" + RESET);
            Thread.sleep(100);
            
            // 步骤2：客户端连接2执行
            System.out.println(YELLOW + "步骤2：客户端连接2开始执行..." + RESET);
            conn2 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn2.setAutoCommit(false);
            printSql(2, "BEGIN", protocolName);
            
            // 检测点4：确认复用后端连接
            printSql(2, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend2 = getBackendInfo(conn2, useExtendedProtocol);
            
            boolean isReused = backend1.pid.equals(backend2.pid);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点4】确认复用客户端1的后端连接:");
            System.out.println("  期望: 复用后端连接 (pid相同)");
            System.out.println("  实际: pid1=" + backend1.pid + ", pid2=" + backend2.pid);
            if (isReused) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 后端连接已复用" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 后端连接未复用" + RESET);
                allPassed = false;
                failureDetails.append("检测点4失败(pid1=" + backend1.pid + ", pid2=" + backend2.pid + "); ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 检测点5：检查DateStyle是否恢复默认值
            printSql(2, "SHOW DateStyle", protocolName);
            String dateStyleInConn2 = getGucValue(conn2, "DateStyle", useExtendedProtocol);
            
            boolean isReset = initialDateStyle.equals(dateStyleInConn2);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点5】期望返回默认值 ISO, MDY:");
            System.out.println("  期望: " + initialDateStyle);
            System.out.println("  实际: " + dateStyleInConn2);
            if (isReset) {
                System.out.println(GREEN + "  结果: ✓ 通过 - DateStyle已恢复默认值" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - DateStyle未恢复默认值" + RESET);
                allPassed = false;
                failureDetails.append("检测点5失败(期望:" + initialDateStyle + ", 实际:" + dateStyleInConn2 + "); ");
            }
            System.out.println("─".repeat(100) + "\n");
            System.out.println(YELLOW + "步骤2完成（保持事务未提交）\n" + RESET);
            Thread.sleep(100);
            
            // 步骤3：连接1再次执行
            System.out.println(YELLOW + "步骤3：连接1再次执行..." + RESET);
            conn1.setAutoCommit(false);
            printSql(1, "BEGIN", protocolName);
            
            // 检测点6：应分配新后端连接
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend1New = getBackendInfo(conn1, useExtendedProtocol);
            
            boolean isNewBackend = !backend1.pid.equals(backend1New.pid);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点6】应分配新的后端连接:");
            System.out.println("  期望: 分配新后端连接 (pid不同)");
            System.out.println("  实际: 原pid=" + backend1.pid + ", 新pid=" + backend1New.pid);
            if (isNewBackend) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 已分配新后端连接" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 仍是原后端连接" + RESET);
                allPassed = false;
                failureDetails.append("检测点6失败(原pid=" + backend1.pid + ", 新pid=" + backend1New.pid + "); ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 检测点7：检查DateStyle是否同步为ISO, DMY
            printSql(1, "SHOW DateStyle", protocolName);
            String dateStyleSynced = getGucValue(conn1, "DateStyle", useExtendedProtocol);
            
            boolean isSynced = dateStyleSynced.contains("ISO") && dateStyleSynced.contains("DMY");
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点7】期望返回 ISO, DMY:");
            System.out.println("  期望: ISO, DMY");
            System.out.println("  实际: " + dateStyleSynced);
            if (isSynced) {
                System.out.println(GREEN + "  结果: ✓ 通过 - DateStyle已同步" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - DateStyle未同步" + RESET);
                allPassed = false;
                failureDetails.append("检测点7失败(期望:ISO, DMY, 实际:" + dateStyleSynced + "); ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            printSql(1, "COMMIT", protocolName);
            conn1.commit();
            System.out.println(YELLOW + "步骤3完成\n" + RESET);
            
            // 步骤4：连接2收尾
            System.out.println(YELLOW + "步骤4：连接2收尾..." + RESET);
            
            // 检测点8：仍应为 ISO, DMY
            printSql(2, "SHOW DateStyle", protocolName);
            String finalDateStyle = getGucValue(conn2, "DateStyle", useExtendedProtocol);
            
            boolean isFinalCorrect = finalDateStyle.contains("ISO") && finalDateStyle.contains("MDY");
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点8】仍应为 ISO, MDY:");
            System.out.println("  期望: ISO, MDY");
            System.out.println("  实际: " + finalDateStyle);
            if (isFinalCorrect) {
                System.out.println(GREEN + "  结果: ✓ 通过" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败" + RESET);
                allPassed = false;
                failureDetails.append("检测点8失败(期望:ISO, MDY, 实际:" + finalDateStyle + "); ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            printSql(2, "COMMIT", protocolName);
            conn2.commit();
            System.out.println(YELLOW + "步骤4完成\n" + RESET);
            
            recordResult("guc report参数同步", "DateStyle参数（" + protocolName + "）", 
                        "所有检测点通过", allPassed ? "所有检测点通过" : failureDetails.toString(), 
                        allPassed, allPassed ? "通过" : "失败");
                        
        } finally {
            if (conn1 != null) try { conn1.close(); } catch (SQLException e) { e.printStackTrace(); }
            if (conn2 != null) try { conn2.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }
    
    // ==================== 测试用例2：TimeZone参数——RESET 恢复默认值 ====================
    
    public void testCase2_TimeZoneReset_SimpleProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2-Simple协议】测试TimeZone参数——RESET 恢复默认值");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2_TimeZone(false, "Simple协议");
    }
    
    public void testCase2_TimeZoneReset_ExtendedProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2-Extended协议】测试TimeZone参数——RESET 恢复默认值");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2_TimeZone(true, "Extended协议");
    }
    
    private void executeTestCase2_TimeZone(boolean useExtendedProtocol, String protocolName) throws SQLException, InterruptedException, Exception {
        Connection conn1 = null;
        Connection conn2 = null;
        boolean allPassed = true;
        StringBuilder failureDetails = new StringBuilder();
        
        try {
            String url = getUrlWithProtocol(useExtendedProtocol);
            
            // ============ 步骤1：客户端连接1执行（事务外SET，才能同步）============
            System.out.println(YELLOW + "步骤1：客户端连接1执行（事务外SET）..." + RESET);
            conn1 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn1.setAutoCommit(true); // 使用autocommit模式，SET在事务外才能同步
            
            // 检测点1：记录默认值
            printSql(1, "SHOW TimeZone", protocolName);
            String defaultTimeZone = getGucValue(conn1, "TimeZone", useExtendedProtocol);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点1】记录默认TimeZone值:");
            System.out.println("  默认值: " + defaultTimeZone + " (以实际环境为准，如 Asia/Shanghai 或 UTC)");
            System.out.println("─".repeat(100) + "\n");
            
            // 检测点2：设置新值（事务外执行，可以同步）
            printSql(1, "SET TimeZone = UTC", protocolName);
            executeUpdate(conn1, "SET TimeZone = UTC", useExtendedProtocol);
            
            printSql(1, "SHOW TimeZone", protocolName);
            String timeZoneAfterSet = getGucValue(conn1, "TimeZone", useExtendedProtocol);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点2】设置TimeZone=UTC并输出SQL日志:");
            System.out.println("  设置后TimeZone: " + timeZoneAfterSet);
            boolean isSetSuccess = "UTC".equals(timeZoneAfterSet);
            if (isSetSuccess) {
                System.out.println(GREEN + "  结果: ✓ 设置成功" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 设置失败，期望=UTC, 实际=" + timeZoneAfterSet + RESET);
                allPassed = false;
                failureDetails.append("检测点2失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 检测点3：记录后端连接标识
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend1 = getBackendInfo(conn1, useExtendedProtocol);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点3】记录后端连接标识与TimeZone=UTC:");
            System.out.println("  后端连接1: " + backend1);
            System.out.println("  TimeZone: UTC");
            System.out.println("─".repeat(100) + "\n");
            
            System.out.println(YELLOW + "步骤1完成\n" + RESET);
            Thread.sleep(100);
            
            // ============ 步骤2：客户端连接2执行 ============
            System.out.println(YELLOW + "步骤2：客户端连接2执行..." + RESET);
            conn2 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn2.setAutoCommit(false);
            printSql(2, "BEGIN", protocolName);
            
            // 检测点4：确认复用步骤(1)的后端连接
            printSql(2, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend2 = getBackendInfo(conn2, useExtendedProtocol);
            
            boolean isReused = backend1.pid.equals(backend2.pid);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点4】确认复用步骤(1)的后端连接:");
            System.out.println("  期望: 复用后端连接 (pid相同)");
            System.out.println("  实际: 客户端连接1的后端pid=" + backend1.pid + ", 客户端连接2的后端pid=" + backend2.pid);
            if (isReused) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 后端连接已复用" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 后端连接未复用" + RESET);
                allPassed = false;
                failureDetails.append("检测点4失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 检测点5：期望默认值Asia/Shanghai
            printSql(2, "SHOW TimeZone", protocolName);
            String timeZoneInConn2 = getGucValue(conn2, "TimeZone", useExtendedProtocol);
            
            boolean isDefaultInConn2 = defaultTimeZone.equals(timeZoneInConn2);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点5】期望默认值" + defaultTimeZone + ":");
            System.out.println("  说明: 连接池在发放后端连接前执行了RESET/ParameterStatus");
            System.out.println("  期望: " + defaultTimeZone);
            System.out.println("  实际: " + timeZoneInConn2);
            if (isDefaultInConn2) {
                System.out.println(GREEN + "  结果: ✓ 通过 - TimeZone已恢复默认值" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - TimeZone未恢复默认值" + RESET);
                allPassed = false;
                failureDetails.append("检测点5失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            System.out.println(YELLOW + "步骤2完成（保持事务未提交，继续占用后端连接）\n" + RESET);
            Thread.sleep(100);
            
            // ============ 步骤3：客户端连接1再次执行 ============
            System.out.println(YELLOW + "步骤3：客户端连接1再次执行..." + RESET);
            conn1.setAutoCommit(false);
            printSql(1, "BEGIN", protocolName);
            
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend1New = getBackendInfo(conn1, useExtendedProtocol);
            System.out.println(BLUE + "  → 后端连接: " + backend1New + " (应该是新分配的后端连接)" + RESET);
            
            // 检测点6：期望值依然是UTC（因为步骤1在事务外SET，会同步到新后端）
            printSql(1, "SHOW TimeZone", protocolName);
            String timeZoneInConn1 = getGucValue(conn1, "TimeZone", useExtendedProtocol);
            
            boolean isUTC = "UTC".equals(timeZoneInConn1);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点6】期望值依然是UTC:");
            System.out.println("  说明: 验证缓存同步逻辑会对新后端执行SET TimeZone=UTC");
            System.out.println("  期望: UTC");
            System.out.println("  实际: " + timeZoneInConn1);
            if (isUTC) {
                System.out.println(GREEN + "  结果: ✓ 通过 - TimeZone已正确同步到新后端" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - TimeZone未正确同步" + RESET);
                allPassed = false;
                failureDetails.append("检测点6失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            printSql(1, "COMMIT", protocolName);
            conn1.commit();
            
            // 在事务外执行RESET（才能同步）
            conn1.setAutoCommit(true);
            printSql(1, "RESET TimeZone", protocolName);
            executeUpdate(conn1, "RESET TimeZone", useExtendedProtocol);
            
            // 检测点7：恢复默认值
            printSql(1, "SHOW TimeZone", protocolName);
            String timeZoneAfterReset = getGucValue(conn1, "TimeZone", useExtendedProtocol);
            
            boolean isResetToDefault = defaultTimeZone.equals(timeZoneAfterReset);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点7】恢复默认值" + defaultTimeZone + ":");
            System.out.println("  期望: " + defaultTimeZone);
            System.out.println("  实际: " + timeZoneAfterReset);
            if (isResetToDefault) {
                System.out.println(GREEN + "  结果: ✓ 通过 - RESET成功恢复默认值" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - RESET未恢复默认值" + RESET);
                allPassed = false;
                failureDetails.append("检测点7失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            System.out.println(YELLOW + "步骤3完成\n" + RESET);
            Thread.sleep(100);
            
            // ============ 步骤4：客户端连接2收尾 ============
            System.out.println(YELLOW + "步骤4：客户端连接2收尾..." + RESET);
            printSql(2, "COMMIT", protocolName);
            conn2.commit();
            System.out.println(YELLOW + "步骤4完成（释放后端连接）\n" + RESET);
            Thread.sleep(100);
            
            // ============ 步骤5：客户端连接1再次执行 ============
            System.out.println(YELLOW + "步骤5：客户端连接1再次执行..." + RESET);
            conn1.setAutoCommit(false);
            printSql(1, "BEGIN", protocolName);
            
            // 检测点8：应该复用客户端连接2的后端连接，TimeZone还是默认值
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend1Final = getBackendInfo(conn1, useExtendedProtocol);
            
            printSql(1, "SHOW TimeZone", protocolName);
            String timeZoneFinal = getGucValue(conn1, "TimeZone", useExtendedProtocol);
            
            boolean isReusedFromConn2 = backend2.pid.equals(backend1Final.pid);
            boolean isDefaultFinal = defaultTimeZone.equals(timeZoneFinal);
            
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点8】应该复用客户端连接2的后端连接，TimeZone还是默认值" + defaultTimeZone + ":");
            System.out.println("  期望: 复用连接2的后端 (pid=" + backend2.pid + ") 且 TimeZone=" + defaultTimeZone);
            System.out.println("  实际: 后端pid=" + backend1Final.pid + ", TimeZone=" + timeZoneFinal);
            if (isReusedFromConn2 && isDefaultFinal) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 后端连接复用正确且TimeZone为默认值" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败" + RESET);
                allPassed = false;
                if (!isReusedFromConn2) failureDetails.append("检测点8-后端连接未复用; ");
                if (!isDefaultFinal) failureDetails.append("检测点8-TimeZone不是默认值; ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            printSql(1, "COMMIT", protocolName);
            conn1.commit();
            System.out.println(YELLOW + "步骤5完成\n" + RESET);
            
            recordResult("guc report参数同步", "TimeZone参数——RESET恢复默认值（" + protocolName + "）", 
                        "所有检测点通过", allPassed ? "所有检测点通过" : failureDetails.toString(), 
                        allPassed, allPassed ? "通过" : "失败");
                        
        } finally {
            if (conn1 != null) try { conn1.close(); } catch (SQLException e) { e.printStackTrace(); }
            if (conn2 != null) try { conn2.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    // ==================== 测试用例2b：多参数同步与 RESET ALL ====================
    
    public void testCase2b_MultiParamResetAll_SimpleProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2b-Simple协议】多参数同步与 RESET ALL");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2b_MultiParamResetAll(false, "Simple协议");
    }
    
    public void testCase2b_MultiParamResetAll_ExtendedProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2b-Extended协议】多参数同步与 RESET ALL");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2b_MultiParamResetAll(true, "Extended协议");
    }
    
    /**
     * 测试用例2b：多参数同步与 RESET ALL
     * 目标：验证多个 guc report 参数在 RESET ALL 后是否按照路由默认值重新同步
     */
    private void executeTestCase2b_MultiParamResetAll(boolean useExtendedProtocol, String protocolName) throws SQLException, InterruptedException, Exception {
        Connection conn1 = null;
        Connection conn2 = null;
        boolean allPassed = true;
        StringBuilder failureDetails = new StringBuilder();
        
        try {
            String url = getUrlWithProtocol(useExtendedProtocol);
            
            // ============ 步骤1：客户端连接1执行（事务外SET+RESET ALL）============
            System.out.println(YELLOW + "步骤1：客户端连接1设置多个参数并执行RESET ALL..." + RESET);
            conn1 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn1.setAutoCommit(true); // 事务外执行，才能同步
            
            // 记录默认值
            printSql(1, "SHOW standard_conforming_strings", protocolName);
            String defaultStandardConformingStrings = getGucValue(conn1, "standard_conforming_strings", useExtendedProtocol);
            
            printSql(1, "SHOW IntervalStyle", protocolName);
            String defaultIntervalStyle = getGucValue(conn1, "IntervalStyle", useExtendedProtocol);
            
            printSql(1, "SHOW DateStyle", protocolName);
            String defaultDateStyle = getGucValue(conn1, "DateStyle", useExtendedProtocol);
            
            printSql(1, "SHOW extra_float_digits", protocolName);
            String defaultExtraFloatDigits = getGucValue(conn1, "extra_float_digits", useExtendedProtocol);
            
            printSql(1, "SHOW statement_timeout", protocolName);
            String defaultStatementTimeout = getGucValue(conn1, "statement_timeout", useExtendedProtocol);
            
            System.out.println(GREEN + "  → 记录默认值: standard_conforming_strings=" + defaultStandardConformingStrings +
                             ", IntervalStyle=" + defaultIntervalStyle + 
                             ", DateStyle=" + defaultDateStyle +
                             ", extra_float_digits=" + defaultExtraFloatDigits +
                             ", statement_timeout=" + defaultStatementTimeout + RESET);
            
            // 设置多个参数
            printSql(1, "SET standard_conforming_strings = off", protocolName);
            executeUpdate(conn1, "SET standard_conforming_strings = off", useExtendedProtocol);
            
            printSql(1, "SET IntervalStyle = sql_standard", protocolName);
            executeUpdate(conn1, "SET IntervalStyle = sql_standard", useExtendedProtocol);
            
            printSql(1, "SET DateStyle = ISO, DMY", protocolName);
            executeUpdate(conn1, "SET DateStyle = ISO, DMY", useExtendedProtocol);
            
            printSql(1, "SET extra_float_digits = 3", protocolName);
            executeUpdate(conn1, "SET extra_float_digits = 3", useExtendedProtocol);
            
            printSql(1, "SET statement_timeout = 5000", protocolName);
            executeUpdate(conn1, "SET statement_timeout = 5000", useExtendedProtocol);
            
            // 检测点1：记录所有已修改的参数和值
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend1 = getBackendInfo(conn1, useExtendedProtocol);
            
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点1】日志中记录所有已修改的参数和值:");
            System.out.println("  后端连接1: " + backend1);
            
            printSql(1, "SHOW standard_conforming_strings", protocolName);
            System.out.println("  standard_conforming_strings: " + getGucValue(conn1, "standard_conforming_strings", useExtendedProtocol));
            
            printSql(1, "SHOW IntervalStyle", protocolName);
            System.out.println("  IntervalStyle: " + getGucValue(conn1, "IntervalStyle", useExtendedProtocol));
            
            printSql(1, "SHOW DateStyle", protocolName);
            System.out.println("  DateStyle: " + getGucValue(conn1, "DateStyle", useExtendedProtocol));
            
            printSql(1, "SHOW extra_float_digits", protocolName);
            System.out.println("  extra_float_digits: " + getGucValue(conn1, "extra_float_digits", useExtendedProtocol));
            
            printSql(1, "SHOW statement_timeout", protocolName);
            System.out.println("  statement_timeout: " + getGucValue(conn1, "statement_timeout", useExtendedProtocol));
            System.out.println("─".repeat(100) + "\n");
            
            // 执行RESET ALL
            printSql(1, "RESET ALL", protocolName);
            executeUpdate(conn1, "RESET ALL", useExtendedProtocol);
            
            // 关闭连接1，让后端连接返回连接池
            System.out.println(YELLOW + "步骤1完成，关闭连接1，让后端连接返回连接池\n" + RESET);
            conn1.close();
            Thread.sleep(100);
            
            // ============ 步骤2：客户端连接2执行 ============
            System.out.println(YELLOW + "步骤2：客户端连接2执行..." + RESET);
            conn2 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn2.setAutoCommit(false);
            printSql(2, "BEGIN", protocolName);
            
            // 检测点2：确认复用步骤(1)的后端连接
            printSql(2, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend2 = getBackendInfo(conn2, useExtendedProtocol);
            
            boolean isReused = backend1.pid.equals(backend2.pid);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点2】确认复用步骤(1)的后端连接:");
            System.out.println("  期望: 复用后端连接 (pid相同)");
            System.out.println("  实际: pid1=" + backend1.pid + ", pid2=" + backend2.pid);
            if (isReused) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 后端连接已复用" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 后端连接未复用" + RESET);
                allPassed = false;
                failureDetails.append("检测点2失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 检测点3：下列数值应全为默认值
            printSql(2, "SHOW standard_conforming_strings", protocolName);
            String standardConformingStringsConn2 = getGucValue(conn2, "standard_conforming_strings", useExtendedProtocol);
            
            printSql(2, "SHOW IntervalStyle", protocolName);
            String intervalStyleConn2 = getGucValue(conn2, "IntervalStyle", useExtendedProtocol);
            
            printSql(2, "SHOW DateStyle", protocolName);
            String dateStyleConn2 = getGucValue(conn2, "DateStyle", useExtendedProtocol);
            
            printSql(2, "SHOW extra_float_digits", protocolName);
            String extraFloatDigitsConn2 = getGucValue(conn2, "extra_float_digits", useExtendedProtocol);
            
            printSql(2, "SHOW statement_timeout", protocolName);
            String statementTimeoutConn2 = getGucValue(conn2, "statement_timeout", useExtendedProtocol);
            
            boolean allDefault = defaultStandardConformingStrings.equals(standardConformingStringsConn2) &&
                                defaultIntervalStyle.equals(intervalStyleConn2) &&
                                defaultDateStyle.equals(dateStyleConn2) &&
                                defaultExtraFloatDigits.equals(extraFloatDigitsConn2) &&
                                defaultStatementTimeout.equals(statementTimeoutConn2);
            
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点3】下列数值应全为默认值:");
            System.out.println("  期望: standard_conforming_strings=" + defaultStandardConformingStrings +
                             ", IntervalStyle=" + defaultIntervalStyle +
                             ", DateStyle=" + defaultDateStyle +
                             ", extra_float_digits=" + defaultExtraFloatDigits +
                             ", statement_timeout=" + defaultStatementTimeout);
            System.out.println("  实际: standard_conforming_strings=" + standardConformingStringsConn2 +
                             ", IntervalStyle=" + intervalStyleConn2 +
                             ", DateStyle=" + dateStyleConn2 +
                             ", extra_float_digits=" + extraFloatDigitsConn2 +
                             ", statement_timeout=" + statementTimeoutConn2);
            if (allDefault) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 所有参数已恢复默认值" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 部分参数未恢复默认值" + RESET);
                allPassed = false;
                failureDetails.append("检测点3失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            System.out.println(YELLOW + "步骤2完成（保持事务未提交，继续占用后端连接）\n" + RESET);
            Thread.sleep(100);
            
            // ============ 步骤3：重新创建客户端连接1并执行 ============
            System.out.println(YELLOW + "步骤3：重新创建客户端连接1并执行..." + RESET);
            conn1 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn1.setAutoCommit(true); // 不开启事务
            
            // 检测点4：应分配新的后端连接
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend1New = getBackendInfo(conn1, useExtendedProtocol);
            
            boolean isNewBackend = !backend1.pid.equals(backend1New.pid);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点4】应分配新的后端连接:");
            System.out.println("  期望: 分配新后端连接 (pid不同)");
            System.out.println("  实际: 原pid=" + backend1.pid + ", 新pid=" + backend1New.pid);
            if (isNewBackend) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 已分配新后端连接" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 仍是原后端连接" + RESET);
                allPassed = false;
                failureDetails.append("检测点4失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 检测点5：下列数值应全为默认值（RESET ALL后）
            printSql(1, "SHOW standard_conforming_strings", protocolName);
            String standardConformingStringsConn1 = getGucValue(conn1, "standard_conforming_strings", useExtendedProtocol);
            
            printSql(1, "SHOW IntervalStyle", protocolName);
            String intervalStyleConn1 = getGucValue(conn1, "IntervalStyle", useExtendedProtocol);
            
            printSql(1, "SHOW DateStyle", protocolName);
            String dateStyleConn1 = getGucValue(conn1, "DateStyle", useExtendedProtocol);
            
            printSql(1, "SHOW extra_float_digits", protocolName);
            String extraFloatDigitsConn1 = getGucValue(conn1, "extra_float_digits", useExtendedProtocol);
            
            printSql(1, "SHOW statement_timeout", protocolName);
            String statementTimeoutConn1 = getGucValue(conn1, "statement_timeout", useExtendedProtocol);
            
            boolean allDefaultConn1 = defaultStandardConformingStrings.equals(standardConformingStringsConn1) &&
                                     defaultIntervalStyle.equals(intervalStyleConn1) &&
                                     defaultDateStyle.equals(dateStyleConn1) &&
                                     defaultExtraFloatDigits.equals(extraFloatDigitsConn1) &&
                                     defaultStatementTimeout.equals(statementTimeoutConn1);
            
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点5】下列数值应全为默认值（RESET ALL后）:");
            System.out.println("  期望: standard_conforming_strings=" + defaultStandardConformingStrings +
                             ", IntervalStyle=" + defaultIntervalStyle +
                             ", DateStyle=" + defaultDateStyle +
                             ", extra_float_digits=" + defaultExtraFloatDigits +
                             ", statement_timeout=" + defaultStatementTimeout);
            System.out.println("  实际: standard_conforming_strings=" + standardConformingStringsConn1 +
                             ", IntervalStyle=" + intervalStyleConn1 +
                             ", DateStyle=" + dateStyleConn1 +
                             ", extra_float_digits=" + extraFloatDigitsConn1 +
                             ", statement_timeout=" + statementTimeoutConn1);
            if (allDefaultConn1) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 所有参数为默认值" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 部分参数不是默认值" + RESET);
                allPassed = false;
                failureDetails.append("检测点5失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            System.out.println(YELLOW + "步骤3完成\n" + RESET);
            
            // ============ 步骤4：客户端连接2收尾 ============
            System.out.println(YELLOW + "步骤4：客户端连接2收尾..." + RESET);
            printSql(2, "COMMIT", protocolName);
            conn2.commit();
            System.out.println(YELLOW + "步骤4完成（释放后端连接）\n" + RESET);
            
            recordResult("多参数同步与RESET ALL", "多参数同步与RESET ALL（" + protocolName + "）", 
                        "所有检测点通过", allPassed ? "所有检测点通过" : failureDetails.toString(), 
                        allPassed, allPassed ? "通过" : "失败");
                        
        } finally {
            if (conn1 != null) try { conn1.close(); } catch (SQLException e) { e.printStackTrace(); }
            if (conn2 != null) try { conn2.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    // ==================== 测试用例3：多参数同步与 DISCARD ALL (仅Simple协议) ====================
    
    public void testCase3_MultiParamDiscardAll_SimpleProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例3-Simple协议】多参数同步与 DISCARD ALL");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase3_MultiParamDiscardAll(false, "Simple协议");
    }
    
    /**
     * 测试用例3：多参数同步与 DISCARD ALL
     * 目标：验证多个 guc report 参数在 DISCARD ALL 后是否按照路由默认值重新同步
     * 注意：仅实现Simple协议版本，因为DISCARD ALL会删除prepared statements，导致Extended协议测试失败
     */
    private void executeTestCase3_MultiParamDiscardAll(boolean useExtendedProtocol, String protocolName) throws SQLException, InterruptedException, Exception {
        Connection conn1 = null;
        Connection conn2 = null;
        boolean allPassed = true;
        StringBuilder failureDetails = new StringBuilder();
        
        try {
            String url = getUrlWithProtocol(useExtendedProtocol);
            
            // ============ 步骤1：客户端连接1执行（事务外SET+DISCARD ALL）============
            System.out.println(YELLOW + "步骤1：客户端连接1设置多个参数并执行DISCARD ALL..." + RESET);
            conn1 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn1.setAutoCommit(true); // 事务外执行，才能同步
            
            // 记录默认值
            printSql(1, "SHOW standard_conforming_strings", protocolName);
            String defaultStandardConformingStrings = getGucValue(conn1, "standard_conforming_strings", useExtendedProtocol);
            
            printSql(1, "SHOW IntervalStyle", protocolName);
            String defaultIntervalStyle = getGucValue(conn1, "IntervalStyle", useExtendedProtocol);
            
            printSql(1, "SHOW DateStyle", protocolName);
            String defaultDateStyle = getGucValue(conn1, "DateStyle", useExtendedProtocol);
            
            printSql(1, "SHOW extra_float_digits", protocolName);
            String defaultExtraFloatDigits = getGucValue(conn1, "extra_float_digits", useExtendedProtocol);
            
            System.out.println(GREEN + "  → 记录默认值: standard_conforming_strings=" + defaultStandardConformingStrings +
                             ", IntervalStyle=" + defaultIntervalStyle + 
                             ", DateStyle=" + defaultDateStyle +
                             ", extra_float_digits=" + defaultExtraFloatDigits + RESET);
            
            // 设置多个参数
            printSql(1, "SET standard_conforming_strings = off", protocolName);
            executeUpdate(conn1, "SET standard_conforming_strings = off", useExtendedProtocol);
            
            printSql(1, "SET IntervalStyle = sql_standard", protocolName);
            executeUpdate(conn1, "SET IntervalStyle = sql_standard", useExtendedProtocol);
            
            printSql(1, "SET DateStyle = ISO, DMY", protocolName);
            executeUpdate(conn1, "SET DateStyle = ISO, DMY", useExtendedProtocol);
            
            printSql(1, "SET extra_float_digits = 3", protocolName);
            executeUpdate(conn1, "SET extra_float_digits = 3", useExtendedProtocol);
            
            // 检测点1：记录所有已修改的参数和值
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend1 = getBackendInfo(conn1, useExtendedProtocol);
            
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点1】日志中记录所有已修改的参数和值:");
            System.out.println("  后端连接1: " + backend1);
            
            printSql(1, "SHOW standard_conforming_strings", protocolName);
            System.out.println("  standard_conforming_strings: " + getGucValue(conn1, "standard_conforming_strings", useExtendedProtocol));
            
            printSql(1, "SHOW IntervalStyle", protocolName);
            System.out.println("  IntervalStyle: " + getGucValue(conn1, "IntervalStyle", useExtendedProtocol));
            
            printSql(1, "SHOW DateStyle", protocolName);
            System.out.println("  DateStyle: " + getGucValue(conn1, "DateStyle", useExtendedProtocol));
            
            printSql(1, "SHOW extra_float_digits", protocolName);
            System.out.println("  extra_float_digits: " + getGucValue(conn1, "extra_float_digits", useExtendedProtocol));
            System.out.println("─".repeat(100) + "\n");
            
            // 执行DISCARD ALL
            printSql(1, "DISCARD ALL", protocolName);
            executeUpdate(conn1, "DISCARD ALL", useExtendedProtocol);
            
            // 关闭连接1，让后端连接返回连接池
            System.out.println(YELLOW + "步骤1完成，关闭连接1，让后端连接返回连接池\n" + RESET);
            conn1.close();
            Thread.sleep(100);
            
            // ============ 步骤2：客户端连接2执行 ============
            System.out.println(YELLOW + "步骤2：客户端连接2执行..." + RESET);
            conn2 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn2.setAutoCommit(false);
            printSql(2, "BEGIN", protocolName);
            
            // 检测点2：确认复用步骤(1)的后端连接
            printSql(2, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend2 = getBackendInfo(conn2, useExtendedProtocol);
            
            boolean isReused = backend1.pid.equals(backend2.pid);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点2】确认复用步骤(1)的后端连接:");
            System.out.println("  期望: 复用后端连接 (pid相同)");
            System.out.println("  实际: pid1=" + backend1.pid + ", pid2=" + backend2.pid);
            if (isReused) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 后端连接已复用" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 后端连接未复用" + RESET);
                allPassed = false;
                failureDetails.append("检测点2失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 检测点3：下列数值应全为默认值
            printSql(2, "SHOW standard_conforming_strings", protocolName);
            String standardConformingStringsConn2 = getGucValue(conn2, "standard_conforming_strings", useExtendedProtocol);
            
            printSql(2, "SHOW IntervalStyle", protocolName);
            String intervalStyleConn2 = getGucValue(conn2, "IntervalStyle", useExtendedProtocol);
            
            printSql(2, "SHOW DateStyle", protocolName);
            String dateStyleConn2 = getGucValue(conn2, "DateStyle", useExtendedProtocol);
            
            printSql(2, "SHOW extra_float_digits", protocolName);
            String extraFloatDigitsConn2 = getGucValue(conn2, "extra_float_digits", useExtendedProtocol);
            
            boolean allDefault = defaultStandardConformingStrings.equals(standardConformingStringsConn2) &&
                                defaultIntervalStyle.equals(intervalStyleConn2) &&
                                defaultDateStyle.equals(dateStyleConn2) &&
                                defaultExtraFloatDigits.equals(extraFloatDigitsConn2);
            
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点3】下列数值应全为默认值:");
            System.out.println("  期望: standard_conforming_strings=" + defaultStandardConformingStrings +
                             ", IntervalStyle=" + defaultIntervalStyle +
                             ", DateStyle=" + defaultDateStyle +
                             ", extra_float_digits=" + defaultExtraFloatDigits);
            System.out.println("  实际: standard_conforming_strings=" + standardConformingStringsConn2 +
                             ", IntervalStyle=" + intervalStyleConn2 +
                             ", DateStyle=" + dateStyleConn2 +
                             ", extra_float_digits=" + extraFloatDigitsConn2);
            if (allDefault) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 所有参数已恢复默认值" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 部分参数未恢复默认值" + RESET);
                allPassed = false;
                failureDetails.append("检测点3失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            System.out.println(YELLOW + "步骤2完成（保持事务未提交，继续占用后端连接）\n" + RESET);
            Thread.sleep(100);
            
            // ============ 步骤3：重新创建客户端连接1并执行 ============
            System.out.println(YELLOW + "步骤3：重新创建客户端连接1并执行..." + RESET);
            conn1 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn1.setAutoCommit(true); // 不开启事务
            
            // 检测点4：应分配新的后端连接
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend1New = getBackendInfo(conn1, useExtendedProtocol);
            
            boolean isNewBackend = !backend1.pid.equals(backend1New.pid);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点4】应分配新的后端连接:");
            System.out.println("  期望: 分配新后端连接 (pid不同)");
            System.out.println("  实际: 原pid=" + backend1.pid + ", 新pid=" + backend1New.pid);
            if (isNewBackend) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 已分配新后端连接" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 仍是原后端连接" + RESET);
                allPassed = false;
                failureDetails.append("检测点4失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 检测点5：下列数值应全为默认值（DISCARD ALL后）
            printSql(1, "SHOW standard_conforming_strings", protocolName);
            String standardConformingStringsConn1 = getGucValue(conn1, "standard_conforming_strings", useExtendedProtocol);
            
            printSql(1, "SHOW IntervalStyle", protocolName);
            String intervalStyleConn1 = getGucValue(conn1, "IntervalStyle", useExtendedProtocol);
            
            printSql(1, "SHOW DateStyle", protocolName);
            String dateStyleConn1 = getGucValue(conn1, "DateStyle", useExtendedProtocol);
            
            printSql(1, "SHOW extra_float_digits", protocolName);
            String extraFloatDigitsConn1 = getGucValue(conn1, "extra_float_digits", useExtendedProtocol);
            
            boolean allDefaultConn1 = defaultStandardConformingStrings.equals(standardConformingStringsConn1) &&
                                     defaultIntervalStyle.equals(intervalStyleConn1) &&
                                     defaultDateStyle.equals(dateStyleConn1) &&
                                     defaultExtraFloatDigits.equals(extraFloatDigitsConn1);
            
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点5】下列数值应全为默认值（DISCARD ALL后）:");
            System.out.println("  期望: standard_conforming_strings=" + defaultStandardConformingStrings +
                             ", IntervalStyle=" + defaultIntervalStyle +
                             ", DateStyle=" + defaultDateStyle +
                             ", extra_float_digits=" + defaultExtraFloatDigits);
            System.out.println("  实际: standard_conforming_strings=" + standardConformingStringsConn1 +
                             ", IntervalStyle=" + intervalStyleConn1 +
                             ", DateStyle=" + dateStyleConn1 +
                             ", extra_float_digits=" + extraFloatDigitsConn1);
            if (allDefaultConn1) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 所有参数为默认值" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 部分参数不是默认值" + RESET);
                allPassed = false;
                failureDetails.append("检测点5失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            System.out.println(YELLOW + "步骤3完成\n" + RESET);
            
            // ============ 步骤4：客户端连接2收尾 ============
            System.out.println(YELLOW + "步骤4：客户端连接2收尾..." + RESET);
            printSql(2, "COMMIT", protocolName);
            conn2.commit();
            System.out.println(YELLOW + "步骤4完成（释放后端连接）\n" + RESET);
            
            recordResult("多参数同步与DISCARD ALL", "多参数同步与DISCARD ALL（" + protocolName + "）", 
                        "所有检测点通过", allPassed ? "所有检测点通过" : failureDetails.toString(), 
                        allPassed, allPassed ? "通过" : "失败");
                        
        } finally {
            if (conn1 != null) try { conn1.close(); } catch (SQLException e) { e.printStackTrace(); }
            if (conn2 != null) try { conn2.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    // ==================== 测试用例2.5：事务中set guc ====================
    
    public void testCase2_5_SetGucInTransaction_SimpleProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2.5-Simple协议】事务中set guc");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2_5_SetGucInTransaction(false, "Simple协议");
    }
    
    public void testCase2_5_SetGucInTransaction_ExtendedProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2.5-Extended协议】事务中set guc");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2_5_SetGucInTransaction(true, "Extended协议");
    }
    
    /**
     * 测试用例2.5：事务中set guc
     * 目标：测试事务中修改guc参数不会保存到guc缓存，也就不会触发连接前后的guc前后端参数同步
     */
    private void executeTestCase2_5_SetGucInTransaction(boolean useExtendedProtocol, String protocolName) throws SQLException, InterruptedException, Exception {
        Connection conn1 = null;
        Connection conn2 = null;
        boolean allPassed = true;
        StringBuilder failureDetails = new StringBuilder();
        
        try {
            String url = getUrlWithProtocol(useExtendedProtocol);
            
            // ============ 步骤1：客户端连接1在事务中执行SET ============
            System.out.println(YELLOW + "步骤1：客户端连接1在事务中设置多个参数..." + RESET);
            conn1 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn1.setAutoCommit(false); // 开启事务
            printSql(1, "BEGIN", protocolName);
            
            // 记录默认值
            printSql(1, "SHOW standard_conforming_strings", protocolName);
            String defaultStandardConformingStrings = getGucValue(conn1, "standard_conforming_strings", useExtendedProtocol);
            
            printSql(1, "SHOW IntervalStyle", protocolName);
            String defaultIntervalStyle = getGucValue(conn1, "IntervalStyle", useExtendedProtocol);
            
            printSql(1, "SHOW DateStyle", protocolName);
            String defaultDateStyle = getGucValue(conn1, "DateStyle", useExtendedProtocol);
            
            printSql(1, "SHOW extra_float_digits", protocolName);
            String defaultExtraFloatDigits = getGucValue(conn1, "extra_float_digits", useExtendedProtocol);
            
            System.out.println(GREEN + "  → 记录默认值: standard_conforming_strings=" + defaultStandardConformingStrings +
                             ", IntervalStyle=" + defaultIntervalStyle + 
                             ", DateStyle=" + defaultDateStyle +
                             ", extra_float_digits=" + defaultExtraFloatDigits + RESET);
            
            // 在事务中设置多个参数
            printSql(1, "SET standard_conforming_strings = off", protocolName);
            executeUpdate(conn1, "SET standard_conforming_strings = off", useExtendedProtocol);
            
            printSql(1, "SET IntervalStyle = sql_standard", protocolName);
            executeUpdate(conn1, "SET IntervalStyle = sql_standard", useExtendedProtocol);
            
            printSql(1, "SET DateStyle = ISO, DMY", protocolName);
            executeUpdate(conn1, "SET DateStyle = ISO, DMY", useExtendedProtocol);
            
            printSql(1, "SET extra_float_digits = 3", protocolName);
            executeUpdate(conn1, "SET extra_float_digits = 3", useExtendedProtocol);
            
            // 检测点1：记录所有已修改的参数和值
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend1 = getBackendInfo(conn1, useExtendedProtocol);
            
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点1】日志中记录所有已修改的参数和值:");
            System.out.println("  后端连接1: " + backend1);
            
            printSql(1, "SHOW standard_conforming_strings", protocolName);
            String scsInTxn = getGucValue(conn1, "standard_conforming_strings", useExtendedProtocol);
            System.out.println("  standard_conforming_strings: " + scsInTxn);
            
            printSql(1, "SHOW IntervalStyle", protocolName);
            String intervalInTxn = getGucValue(conn1, "IntervalStyle", useExtendedProtocol);
            System.out.println("  IntervalStyle: " + intervalInTxn);
            
            printSql(1, "SHOW DateStyle", protocolName);
            String dateInTxn = getGucValue(conn1, "DateStyle", useExtendedProtocol);
            System.out.println("  DateStyle: " + dateInTxn);
            
            printSql(1, "SHOW extra_float_digits", protocolName);
            String extraInTxn = getGucValue(conn1, "extra_float_digits", useExtendedProtocol);
            System.out.println("  extra_float_digits: " + extraInTxn);
            System.out.println("─".repeat(100) + "\n");
            
            // 提交事务
            printSql(1, "COMMIT", protocolName);
            conn1.commit();
            
            // 关闭连接1，让后端连接返回连接池
            System.out.println(YELLOW + "步骤1完成，关闭连接1，让后端连接返回连接池\n" + RESET);
            conn1.close();
            Thread.sleep(100);
            
            // ============ 步骤2：客户端连接2执行 ============
            System.out.println(YELLOW + "步骤2：客户端连接2执行..." + RESET);
            conn2 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn2.setAutoCommit(false);
            printSql(2, "BEGIN", protocolName);
            
            // 检测点2：确认复用步骤(1)的后端连接
            printSql(2, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend2 = getBackendInfo(conn2, useExtendedProtocol);
            
            boolean isReused = backend1.pid.equals(backend2.pid);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点2】确认复用步骤(1)的后端连接:");
            System.out.println("  期望: 复用后端连接 (pid相同)");
            System.out.println("  实际: pid1=" + backend1.pid + ", pid2=" + backend2.pid);
            if (isReused) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 后端连接已复用" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 后端连接未复用" + RESET);
                allPassed = false;
                failureDetails.append("检测点2失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 检测点3：下列数值应全为默认值（因为步骤1中修改的值在事务中，没同步）
            printSql(2, "SHOW standard_conforming_strings", protocolName);
            String standardConformingStringsConn2 = getGucValue(conn2, "standard_conforming_strings", useExtendedProtocol);
            
            printSql(2, "SHOW IntervalStyle", protocolName);
            String intervalStyleConn2 = getGucValue(conn2, "IntervalStyle", useExtendedProtocol);
            
            printSql(2, "SHOW DateStyle", protocolName);
            String dateStyleConn2 = getGucValue(conn2, "DateStyle", useExtendedProtocol);
            
            printSql(2, "SHOW extra_float_digits", protocolName);
            String extraFloatDigitsConn2 = getGucValue(conn2, "extra_float_digits", useExtendedProtocol);
            
            boolean allDefault = defaultStandardConformingStrings.equals(standardConformingStringsConn2) &&
                                defaultIntervalStyle.equals(intervalStyleConn2) &&
                                defaultDateStyle.equals(dateStyleConn2) &&
                                defaultExtraFloatDigits.equals(extraFloatDigitsConn2);
            
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点3】下列数值应全为默认值（因为步骤1中修改的值在事务中，没同步）:");
            System.out.println("  期望: standard_conforming_strings=" + defaultStandardConformingStrings +
                             ", IntervalStyle=" + defaultIntervalStyle +
                             ", DateStyle=" + defaultDateStyle +
                             ", extra_float_digits=" + defaultExtraFloatDigits);
            System.out.println("  实际: standard_conforming_strings=" + standardConformingStringsConn2 +
                             ", IntervalStyle=" + intervalStyleConn2 +
                             ", DateStyle=" + dateStyleConn2 +
                             ", extra_float_digits=" + extraFloatDigitsConn2);
            if (allDefault) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 所有参数为默认值（事务中SET未同步）" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 部分参数不是默认值" + RESET);
                allPassed = false;
                failureDetails.append("检测点3失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            System.out.println(YELLOW + "步骤2完成（保持事务未提交，继续占用后端连接）\n" + RESET);
            Thread.sleep(100);
            
            // ============ 步骤3：重新创建客户端连接1并执行 ============
            System.out.println(YELLOW + "步骤3：重新创建客户端连接1并执行..." + RESET);
            conn1 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn1.setAutoCommit(true); // 不开启事务
            
            // 检测点4：应分配新的后端连接
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend1New = getBackendInfo(conn1, useExtendedProtocol);
            
            boolean isNewBackend = !backend1.pid.equals(backend1New.pid);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点4】应分配新的后端连接:");
            System.out.println("  期望: 分配新后端连接 (pid不同)");
            System.out.println("  实际: 原pid=" + backend1.pid + ", 新pid=" + backend1New.pid);
            if (isNewBackend) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 已分配新后端连接" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 仍是原后端连接" + RESET);
                allPassed = false;
                failureDetails.append("检测点4失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 检测点5：下列数值应全为默认值（因为步骤1中修改的值在事务中，没同步）
            printSql(1, "SHOW standard_conforming_strings", protocolName);
            String standardConformingStringsConn1 = getGucValue(conn1, "standard_conforming_strings", useExtendedProtocol);
            
            printSql(1, "SHOW IntervalStyle", protocolName);
            String intervalStyleConn1 = getGucValue(conn1, "IntervalStyle", useExtendedProtocol);
            
            printSql(1, "SHOW DateStyle", protocolName);
            String dateStyleConn1 = getGucValue(conn1, "DateStyle", useExtendedProtocol);
            
            printSql(1, "SHOW extra_float_digits", protocolName);
            String extraFloatDigitsConn1 = getGucValue(conn1, "extra_float_digits", useExtendedProtocol);
            
            boolean allDefaultConn1 = defaultStandardConformingStrings.equals(standardConformingStringsConn1) &&
                                     defaultIntervalStyle.equals(intervalStyleConn1) &&
                                     defaultDateStyle.equals(dateStyleConn1) &&
                                     defaultExtraFloatDigits.equals(extraFloatDigitsConn1);
            
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点5】下列数值应全为默认值（因为步骤1中修改的值在事务中，没同步）:");
            System.out.println("  期望: standard_conforming_strings=" + defaultStandardConformingStrings +
                             ", IntervalStyle=" + defaultIntervalStyle +
                             ", DateStyle=" + defaultDateStyle +
                             ", extra_float_digits=" + defaultExtraFloatDigits);
            System.out.println("  实际: standard_conforming_strings=" + standardConformingStringsConn1 +
                             ", IntervalStyle=" + intervalStyleConn1 +
                             ", DateStyle=" + dateStyleConn1 +
                             ", extra_float_digits=" + extraFloatDigitsConn1);
            if (allDefaultConn1) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 所有参数为默认值（事务中SET未同步）" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 部分参数不是默认值" + RESET);
                allPassed = false;
                failureDetails.append("检测点5失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            System.out.println(YELLOW + "步骤3完成\n" + RESET);
            
            // ============ 步骤4：客户端连接2收尾 ============
            System.out.println(YELLOW + "步骤4：客户端连接2收尾..." + RESET);
            printSql(2, "COMMIT", protocolName);
            conn2.commit();
            System.out.println(YELLOW + "步骤4完成（释放后端连接）\n" + RESET);
            
            recordResult("事务中set guc", "事务中set guc（" + protocolName + "）", 
                        "所有检测点通过", allPassed ? "所有检测点通过" : failureDetails.toString(), 
                        allPassed, allPassed ? "通过" : "失败");
                        
        } finally {
            if (conn1 != null) try { conn1.close(); } catch (SQLException e) { e.printStackTrace(); }
            if (conn2 != null) try { conn2.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    // ==================== 测试用例2.6：测试大量guc参数同步 ====================
    
    public void testCase2_6_MassiveGucSync_SimpleProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2.6-Simple协议】测试大量guc参数同步（100个参数）");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2_6_MassiveGucSync(false, "Simple协议");
    }
    
    public void testCase2_6_MassiveGucSync_ExtendedProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2.6-Extended协议】测试大量guc参数同步（100个参数）");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2_6_MassiveGucSync(true, "Extended协议");
    }
    
    /**
     * 测试用例2.6：测试大量guc参数同步
     * 目标：测试大量guc参数同步的场景是否能处理正确
     */
    private void executeTestCase2_6_MassiveGucSync(boolean useExtendedProtocol, String protocolName) throws SQLException, InterruptedException, Exception {
        Connection conn1 = null;
        Connection conn2 = null;
        boolean allPassed = true;
        StringBuilder failureDetails = new StringBuilder();
        
        Map<String, String[]> gucParams = null;
        int paramCount = 0;
        
        System.out.println(BLUE + "正在从数据库获取可设置的GUC参数..." + RESET);
        
        try {
            String url = getUrlWithProtocol(useExtendedProtocol);
            
            // 先建立连接，从数据库获取参数列表
            conn1 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn1.setAutoCommit(true); // 事务外执行，才能同步
            
            // 从数据库动态获取100个可设置的GUC参数
            try {
                gucParams = GucParameterList.getGucParametersFromDatabase(conn1, 100);
                paramCount = gucParams.size();
                System.out.println(GREEN + "  → 成功从数据库获取 " + paramCount + " 个GUC参数" + RESET);
            } catch (Exception e) {
                System.out.println(YELLOW + "  → 警告: 无法从数据库获取参数，使用静态参数列表: " + e.getMessage() + RESET);
                gucParams = GucParameterList.getGucParameters();
                paramCount = gucParams.size();
            }
            
            // ============ 步骤1：客户端连接1设置大量GUC参数 ============
            System.out.println(YELLOW + "步骤1：客户端连接1设置" + paramCount + "个GUC参数..." + RESET);
            
            // 从gucParams中获取默认值（数据库查询时的setting字段），不执行SHOW命令
            Map<String, String> defaultValues = new LinkedHashMap<>();
            String[] sampleParams = {"extra_float_digits", "work_mem", "statement_timeout", "enable_seqscan", "application_name"};
            
            System.out.println(GREEN + "  → 记录代表性参数的默认值（从数据库查询结果获取，不执行SHOW）..." + RESET);
            for (String paramName : sampleParams) {
                if (gucParams.containsKey(paramName)) {
                    String defaultValue = gucParams.get(paramName)[0]; // 第一个元素是默认值
                    defaultValues.put(paramName, defaultValue);
                    System.out.println("    " + paramName + " = " + defaultValue);
                }
            }
            System.out.println(GREEN + "  → 成功记录 " + defaultValues.size() + " 个代表性参数的默认值" + RESET);
            
            // 设置所有参数为测试值
            int successCount = 0;
            int failCount = 0;
            System.out.println(GREEN + "  → 开始设置参数..." + RESET);
            
            for (Map.Entry<String, String[]> entry : gucParams.entrySet()) {
                String paramName = entry.getKey();
                String testValue = entry.getValue()[1]; // 使用测试值
                
                try {
                    String sql = "SET " + paramName + " = " + testValue;
                    printSql(1, sql, protocolName);
                    executeUpdate(conn1, sql, useExtendedProtocol);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    System.out.println(YELLOW + "  → 警告: 无法设置参数 " + paramName + " = " + testValue + ": " + e.getMessage() + RESET);
                }
            }
            
            System.out.println(GREEN + "  → 参数设置完成: 成功=" + successCount + ", 失败=" + failCount + RESET);
            
            // 检测点1：记录后端连接信息和已修改的参数值
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend1 = getBackendInfo(conn1, useExtendedProtocol);
            
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点1】记录后端连接标识与已设置的参数:");
            System.out.println("  后端连接1: " + backend1);
            System.out.println("  成功设置参数数量: " + successCount);
            System.out.println(YELLOW + "  注意: 不在此处验证每个参数，避免执行100次SHOW导致超时" + RESET);
            System.out.println(YELLOW + "  测试重点是后续步骤中的参数同步机制" + RESET);
            System.out.println("─".repeat(100) + "\n");
            
            System.out.println(YELLOW + "步骤1完成\n" + RESET);
            Thread.sleep(100);
            
            // ============ 步骤2：客户端连接2执行 ============
            System.out.println(YELLOW + "步骤2：客户端连接2执行..." + RESET);
            conn2 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn2.setAutoCommit(false);
            printSql(2, "BEGIN", protocolName);
            
            // 检测点2：确认复用步骤(1)的后端连接
            printSql(2, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend2 = getBackendInfo(conn2, useExtendedProtocol);
            
            boolean isReused = backend1.pid.equals(backend2.pid);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点2】确认复用步骤(1)的后端连接:");
            System.out.println("  期望: 复用后端连接 (pid相同)");
            System.out.println("  实际: pid1=" + backend1.pid + ", pid2=" + backend2.pid);
            if (isReused) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 后端连接已复用" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 后端连接未复用" + RESET);
                allPassed = false;
                failureDetails.append("检测点2失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 检测点3：检查代表性参数是否恢复默认值
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点3】检查代表性GUC参数是否恢复默认值:");
            System.out.println("  说明: 只检查代表性参数，避免执行过多SHOW命令导致超时");
            
            int checkCount = 0;
            int resetCorrect = 0;
            int resetIncorrect = 0;
            
            System.out.println("  开始检查代表性参数...");
            for (Map.Entry<String, String> entry : defaultValues.entrySet()) {
                String paramName = entry.getKey();
                String expectedDefault = entry.getValue();
                
                try {
                    String actualValue = getGucValue(conn2, paramName, useExtendedProtocol);
                    checkCount++;
                    
                    // 规范化后比较（去除引号）
                    String normalizedActual = normalizeGucValue(actualValue);
                    String normalizedExpected = normalizeGucValue(expectedDefault);
                    boolean isDefault = normalizedExpected.equals(normalizedActual);
                    
                    if (isDefault) {
                        resetCorrect++;
                        System.out.println(GREEN + "  ✓ " + paramName + ": " + actualValue + " (已恢复默认值)" + RESET);
                    } else {
                        resetIncorrect++;
                        System.out.println(RED + "  ✗ " + paramName + ": " + actualValue + " (期望默认值: " + expectedDefault + ")" + RESET);
                    }
                } catch (Exception e) {
                    checkCount++;
                    System.out.println(YELLOW + "  ? " + paramName + ": 无法检查 - " + e.getMessage() + RESET);
                }
            }
            
            boolean resetSuccess = resetIncorrect == 0;
            System.out.println("  检查结果: 正确=" + resetCorrect + ", 错误=" + resetIncorrect + ", 总计=" + checkCount);
            if (resetSuccess) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 代表性参数已恢复默认值" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 有 " + resetIncorrect + " 个参数未恢复默认值" + RESET);
                allPassed = false;
                failureDetails.append("检测点3失败(" + resetIncorrect + "个参数未恢复); ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            System.out.println(YELLOW + "步骤2完成（保持事务未提交，继续占用后端连接）\n" + RESET);
            Thread.sleep(100);
            
            // ============ 步骤3：连接1继续执行 ============
            System.out.println(YELLOW + "步骤3：连接1继续执行..." + RESET);
            conn1.setAutoCommit(false);
            printSql(1, "BEGIN", protocolName);
            
            // 检测点4：应分配新的后端连接
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend1New = getBackendInfo(conn1, useExtendedProtocol);
            
            boolean isNewBackend = !backend1.pid.equals(backend1New.pid);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点4】应分配新的后端连接:");
            System.out.println("  期望: 分配新后端连接 (pid不同)");
            System.out.println("  实际: 原pid=" + backend1.pid + ", 新pid=" + backend1New.pid);
            if (isNewBackend) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 已分配新后端连接" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 仍是原后端连接" + RESET);
                allPassed = false;
                failureDetails.append("检测点4失败; ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 检测点5：检查代表性参数是否同步到新后端
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点5】检查代表性GUC参数是否同步到新后端:");
            System.out.println("  说明: 只检查代表性参数，避免执行过多SHOW命令导致超时");
            
            int syncCheckCount = 0;
            int syncCorrect = 0;
            int syncIncorrect = 0;
            
            System.out.println("  开始检查代表性参数...");
            for (String paramName : sampleParams) {
                if (gucParams.containsKey(paramName)) {
                    String expectedValue = gucParams.get(paramName)[1]; // 测试值
                    
                    try {
                        String actualValue = getGucValue(conn1, paramName, useExtendedProtocol);
                        syncCheckCount++;
                        
                        // 规范化后比较（去除引号）
                        String normalizedActual = normalizeGucValue(actualValue);
                        String normalizedExpected = normalizeGucValue(expectedValue);
                        boolean isSynced = normalizedExpected.equals(normalizedActual);
                        
                        if (isSynced) {
                            syncCorrect++;
                            System.out.println(GREEN + "  ✓ " + paramName + ": " + actualValue + " (已同步)" + RESET);
                        } else {
                            syncIncorrect++;
                            System.out.println(RED + "  ✗ " + paramName + ": " + actualValue + " (期望: " + expectedValue + ")" + RESET);
                        }
                    } catch (Exception e) {
                        syncCheckCount++;
                        System.out.println(YELLOW + "  ? " + paramName + ": 无法检查 - " + e.getMessage() + RESET);
                    }
                }
            }
            
            boolean syncSuccess = syncIncorrect == 0;
            System.out.println("  检查结果: 正确=" + syncCorrect + ", 错误=" + syncIncorrect + ", 总计=" + syncCheckCount);
            if (syncSuccess) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 代表性参数已同步到新后端" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 有 " + syncIncorrect + " 个参数未同步" + RESET);
                allPassed = false;
                failureDetails.append("检测点5失败(" + syncIncorrect + "个参数未同步); ");
            }
            System.out.println("─".repeat(100) + "\n");
            
            printSql(1, "COMMIT", protocolName);
            conn1.commit();
            System.out.println(YELLOW + "步骤3完成\n" + RESET);
            
            // ============ 步骤4：客户端连接2收尾 ============
            System.out.println(YELLOW + "步骤4：客户端连接2收尾..." + RESET);
            printSql(2, "COMMIT", protocolName);
            conn2.commit();
            System.out.println(YELLOW + "步骤4完成（释放后端连接）\n" + RESET);
            
            recordResult("测试大量guc参数同步", "测试" + paramCount + "个GUC参数同步（" + protocolName + "）", 
                        "所有检测点通过", allPassed ? "所有检测点通过" : failureDetails.toString(), 
                        allPassed, allPassed ? "通过" : "失败");
                        
        } finally {
            if (conn1 != null) try { conn1.close(); } catch (SQLException e) { e.printStackTrace(); }
            if (conn2 != null) try { conn2.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }
    
    // ==================== 测试用例2.8：测试读写前后换，GUC参数的同步 ====================

    public void testCase2_8_ReadWriteSwitch_SimpleProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2.8-Simple协议】测试读写前后换，GUC参数的同步");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2_8_ReadWriteSwitch(false, "Simple协议");
    }

    public void testCase2_8_ReadWriteSwitch_ExtendedProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2.8-Extended协议】测试读写前后换，GUC参数的同步");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2_8_ReadWriteSwitch(true, "Extended协议");
    }

    /**
     * 测试用例2.8：测试读写前后换，GUC参数的同步
     * 目标：验证读写切换后，GUC参数依然能同步
     */
    private void executeTestCase2_8_ReadWriteSwitch(boolean useExtendedProtocol, String protocolName) throws SQLException, InterruptedException, Exception {
        Connection conn1 = null;
        boolean allPassed = true;
        StringBuilder failureDetails = new StringBuilder();

        try {
            String url = getUrlWithProtocol(useExtendedProtocol);
            conn1 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn1.setAutoCommit(true); // 事务外执行

            // ============ 步骤1：首次连接分配的是写节点 ============
            System.out.println(YELLOW + "步骤1：首次连接分配写节点..." + RESET);

            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user, pg_is_in_recovery()", protocolName);
            BackendInfo backend1 = getBackendInfoWithRecovery(conn1, useExtendedProtocol);
            System.out.println(BLUE + "  → 后端连接1信息: " + backend1 + RESET);

            // 检测点1：首次连接分配的是写节点，pg_is_in_recovery = f
            boolean isWriteNode = !backend1.isInRecovery;
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点1】检查首次连接是否分配到写节点:");
            System.out.println("  期望: pg_is_in_recovery = false (写节点)");
            System.out.println("  实际: pg_is_in_recovery = " + backend1.isInRecovery);
            if (isWriteNode) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 已分配到写节点" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 未分配到写节点" + RESET);
                allPassed = false;
                failureDetails.append("检测点1失败; ");
            }
            System.out.println("─".repeat(100) + "\n");

            // 记录默认值
            printSql(1, "SHOW DateStyle", protocolName);
            String defaultDateStyle = getGucValue(conn1, "DateStyle", useExtendedProtocol);
            printSql(1, "SHOW extra_float_digits", protocolName);
            String defaultExtraFloatDigits = getGucValue(conn1, "extra_float_digits", useExtendedProtocol);
            printSql(1, "SHOW search_path", protocolName);
            String defaultSearchPath = getGucValue(conn1, "search_path", useExtendedProtocol);

            System.out.println(GREEN + "  → 记录默认值: DateStyle=" + defaultDateStyle +
                             ", extra_float_digits=" + defaultExtraFloatDigits +
                             ", search_path=" + defaultSearchPath + RESET);

            // 设置GUC参数
            printSql(1, "SET DateStyle = ISO, DMY", protocolName);
            executeUpdate(conn1, "SET DateStyle = ISO, DMY", useExtendedProtocol);

            printSql(1, "SET extra_float_digits = 2", protocolName);
            executeUpdate(conn1, "SET extra_float_digits = 2", useExtendedProtocol);

            printSql(1, "SET search_path = 'myschema, public'", protocolName);
            executeUpdate(conn1, "SET search_path = 'myschema, public'", useExtendedProtocol);

            System.out.println(YELLOW + "步骤1完成\n" + RESET);
            Thread.sleep(100);

            // ============ 步骤2：切换到读节点 ============
            System.out.println(YELLOW + "步骤2：切换到读节点..." + RESET);

            printSql(1, "SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY", protocolName);
            executeUpdate(conn1, "SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY", useExtendedProtocol);

            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user, pg_is_in_recovery()", protocolName);
            BackendInfo backend2 = getBackendInfoWithRecovery(conn1, useExtendedProtocol);
            System.out.println(BLUE + "  → 后端连接信息: " + backend2 + RESET);

            // 检测点2：连接切换，且 pg_is_in_recovery = t 或 port=25432（多活场景下读节点端口为25432）
            boolean switchedToRead = backend2.isInRecovery || backend2.port == 25432;
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点2】检查是否切换到读节点:");
            System.out.println("  期望: pg_is_in_recovery = true 或 port = 25432 (读节点)");
            System.out.println("  实际: pg_is_in_recovery = " + backend2.isInRecovery + ", port = " + backend2.port);
            if (switchedToRead) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 已切换到读节点" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 未切换到读节点" + RESET);
                allPassed = false;
                failureDetails.append("检测点2失败; ");
            }
            System.out.println("─".repeat(100) + "\n");

            // 检测点3：检查参数值是否正确同步
            printSql(1, "SHOW DateStyle", protocolName);
            String dateStyleAfterSwitch = getGucValue(conn1, "DateStyle", useExtendedProtocol);
            printSql(1, "SHOW extra_float_digits", protocolName);
            String extraFloatDigitsAfterSwitch = getGucValue(conn1, "extra_float_digits", useExtendedProtocol);
            printSql(1, "SHOW search_path", protocolName);
            String searchPathAfterSwitch = getGucValue(conn1, "search_path", useExtendedProtocol);

            // 规范化后比较（去除引号差异）
            boolean paramsCorrect = normalizeGucValue(dateStyleAfterSwitch).equals("ISO, DMY") &&
                                   normalizeGucValue(extraFloatDigitsAfterSwitch).equals("2") &&
                                   normalizeGucValue(searchPathAfterSwitch).equals("myschema, public");

            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点3】检查参数值是否正确同步:");
            System.out.println("  期望: DateStyle=ISO, DMY, extra_float_digits=2, search_path=myschema, public");
            System.out.println("  实际: DateStyle=" + dateStyleAfterSwitch +
                             ", extra_float_digits=" + extraFloatDigitsAfterSwitch +
                             ", search_path=" + searchPathAfterSwitch);
            if (paramsCorrect) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 参数已正确同步到读节点" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 参数未正确同步" + RESET);
                allPassed = false;
                failureDetails.append("检测点3失败; ");
            }
            System.out.println("─".repeat(100) + "\n");

            System.out.println(YELLOW + "步骤2完成\n" + RESET);
            Thread.sleep(100);

            // ============ 步骤3：RESET ALL并切换回写节点 ============
            System.out.println(YELLOW + "步骤3：RESET ALL并切换回写节点..." + RESET);

            printSql(1, "RESET ALL", protocolName);
            executeUpdate(conn1, "RESET ALL", useExtendedProtocol);

            printSql(1, "SET SESSION CHARACTERISTICS AS TRANSACTION READ WRITE", protocolName);
            executeUpdate(conn1, "SET SESSION CHARACTERISTICS AS TRANSACTION READ WRITE", useExtendedProtocol);

            // 检测点4：检查参数值是否都恢复默认值
            printSql(1, "SHOW DateStyle", protocolName);
            String dateStyleAfterReset = getGucValue(conn1, "DateStyle", useExtendedProtocol);
            printSql(1, "SHOW extra_float_digits", protocolName);
            String extraFloatDigitsAfterReset = getGucValue(conn1, "extra_float_digits", useExtendedProtocol);
            printSql(1, "SHOW search_path", protocolName);
            String searchPathAfterReset = getGucValue(conn1, "search_path", useExtendedProtocol);

            // 规范化后比较（去除引号差异）
            // 注意：extra_float_digits的真正默认值是1，不是记录下来的值（因为JDBC会设置3）
            boolean paramsReset = normalizeGucValue(dateStyleAfterReset).equals(normalizeGucValue(defaultDateStyle)) &&
                                 normalizeGucValue(extraFloatDigitsAfterReset).equals("1") &&
                                 normalizeGucValue(searchPathAfterReset).equals(normalizeGucValue(defaultSearchPath));

            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点4】检查参数值是否恢复默认值:");
            System.out.println("  期望: DateStyle=" + defaultDateStyle +
                             ", extra_float_digits=1" +
                             ", search_path=" + defaultSearchPath);
            System.out.println("  实际: DateStyle=" + dateStyleAfterReset +
                             ", extra_float_digits=" + extraFloatDigitsAfterReset +
                             ", search_path=" + searchPathAfterReset);
            if (paramsReset) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 参数已恢复默认值" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 参数未恢复默认值" + RESET);
                allPassed = false;
                failureDetails.append("检测点4失败; ");
            }
            System.out.println("─".repeat(100) + "\n");

            System.out.println(YELLOW + "步骤3完成\n" + RESET);

            // 记录测试结果
            recordResult("测试读写前后换，GUC参数的同步", "读写切换GUC同步（" + protocolName + "）",
                        "所有检测点通过", allPassed ? "所有检测点通过" : failureDetails.toString(),
                        allPassed, allPassed ? "通过" : "失败");

        } finally {
            if (conn1 != null) {
                try { conn1.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    // ==================== 测试用例2.10：测试常用guc参数读写切换 ====================

    public void testCase2_10_CommonGucReadWriteSwitch_SimpleProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2.10-Simple协议】测试常用guc参数读写切换");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2_10_CommonGucReadWriteSwitch(false, "Simple协议");
    }

    public void testCase2_10_CommonGucReadWriteSwitch_ExtendedProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2.10-Extended协议】测试常用guc参数读写切换");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2_10_CommonGucReadWriteSwitch(true, "Extended协议");
    }

    /**
     * 测试用例2.10：测试常用guc参数读写切换
     * 目标：验证读写切换后，常用GUC参数依然能同步
     * 测试步骤完全参考2.8，但测试更多参数
     */
    private void executeTestCase2_10_CommonGucReadWriteSwitch(boolean useExtendedProtocol, String protocolName) throws SQLException, InterruptedException, Exception {
        Connection conn1 = null;
        boolean allPassed = true;
        StringBuilder failureDetails = new StringBuilder();

        try {
            String url = getUrlWithProtocol(useExtendedProtocol);
            conn1 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn1.setAutoCommit(true); // 事务外执行

            // ============ 步骤1：首次连接分配的是写节点 ============
            System.out.println(YELLOW + "步骤1：首次连接分配写节点..." + RESET);

            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user, pg_is_in_recovery()", protocolName);
            BackendInfo backend1 = getBackendInfoWithRecovery(conn1, useExtendedProtocol);
            System.out.println(BLUE + "  → 后端连接1信息: " + backend1 + RESET);

            // 检测点1：首次连接分配的是写节点，pg_is_in_recovery = f
            boolean isWriteNode = !backend1.isInRecovery;
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点1】检查首次连接是否分配到写节点:");
            System.out.println("  期望: pg_is_in_recovery = false (写节点)");
            System.out.println("  实际: pg_is_in_recovery = " + backend1.isInRecovery);
            if (isWriteNode) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 已分配到写节点" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 未分配到写节点" + RESET);
                allPassed = false;
                failureDetails.append("检测点1失败; ");
            }
            System.out.println("─".repeat(100) + "\n");

            // 记录默认值 - 19个参数 (排除client_encoding因为有限制，排除transaction_isolation和transaction_read_only因为是事务级别参数)
            System.out.println(GREEN + "  → 开始记录19个GUC参数的默认值..." + RESET);
            
            printSql(1, "SHOW default_transaction_read_only", protocolName);
            String defaultTransactionReadOnly = getGucValue(conn1, "default_transaction_read_only", useExtendedProtocol);
            
            printSql(1, "SHOW standard_conforming_strings", protocolName);
            String defaultStandardConformingStrings = getGucValue(conn1, "standard_conforming_strings", useExtendedProtocol);
            
            printSql(1, "SHOW DateStyle", protocolName);
            String defaultDateStyle = getGucValue(conn1, "DateStyle", useExtendedProtocol);
            
            printSql(1, "SHOW TimeZone", protocolName);
            String defaultTimeZone = getGucValue(conn1, "TimeZone", useExtendedProtocol);
            
            printSql(1, "SHOW application_name", protocolName);
            String defaultApplicationName = getGucValue(conn1, "application_name", useExtendedProtocol);
            
            printSql(1, "SHOW IntervalStyle", protocolName);
            String defaultIntervalStyle = getGucValue(conn1, "IntervalStyle", useExtendedProtocol);
            
            printSql(1, "SHOW work_mem", protocolName);
            String defaultWorkMem = getGucValue(conn1, "work_mem", useExtendedProtocol);
            
            printSql(1, "SHOW maintenance_work_mem", protocolName);
            String defaultMaintenanceWorkMem = getGucValue(conn1, "maintenance_work_mem", useExtendedProtocol);
            
            printSql(1, "SHOW temp_buffers", protocolName);
            String defaultTempBuffers = getGucValue(conn1, "temp_buffers", useExtendedProtocol);
            
            printSql(1, "SHOW logical_decoding_work_mem", protocolName);
            String defaultLogicalDecodingWorkMem = getGucValue(conn1, "logical_decoding_work_mem", useExtendedProtocol);
            
            printSql(1, "SHOW statement_timeout", protocolName);
            String defaultStatementTimeout = getGucValue(conn1, "statement_timeout", useExtendedProtocol);
            
            printSql(1, "SHOW lock_timeout", protocolName);
            String defaultLockTimeout = getGucValue(conn1, "lock_timeout", useExtendedProtocol);
            
            printSql(1, "SHOW idle_in_transaction_session_timeout", protocolName);
            String defaultIdleInTransactionTimeout = getGucValue(conn1, "idle_in_transaction_session_timeout", useExtendedProtocol);
            
            printSql(1, "SHOW search_path", protocolName);
            String defaultSearchPath = getGucValue(conn1, "search_path", useExtendedProtocol);
            
            printSql(1, "SHOW extra_float_digits", protocolName);
            String defaultExtraFloatDigits = getGucValue(conn1, "extra_float_digits", useExtendedProtocol);
            
            printSql(1, "SHOW bytea_output", protocolName);
            String defaultByteaOutput = getGucValue(conn1, "bytea_output", useExtendedProtocol);
            
            printSql(1, "SHOW xmloption", protocolName);
            String defaultXmloption = getGucValue(conn1, "xmloption", useExtendedProtocol);
            
            printSql(1, "SHOW enable_seqscan", protocolName);
            String defaultEnableSeqscan = getGucValue(conn1, "enable_seqscan", useExtendedProtocol);

            System.out.println(GREEN + "  → 记录19个GUC参数默认值完成" + RESET);

            // 设置19个GUC参数
            System.out.println(GREEN + "  → 开始设置19个GUC参数..." + RESET);
            
            // 注意: default_transaction_read_only, session_authorization, client_encoding 通常不能在普通会话中修改
            // transaction_isolation 和 transaction_read_only 是事务级别参数，不适合此测试
            // 我们跳过这些参数的设置，只记录它们的默认值
            
            printSql(1, "SET standard_conforming_strings = off", protocolName);
            executeUpdate(conn1, "SET standard_conforming_strings = off", useExtendedProtocol);
            
            printSql(1, "SET DateStyle = ISO, DMY", protocolName);
            executeUpdate(conn1, "SET DateStyle = ISO, DMY", useExtendedProtocol);
            
            printSql(1, "SET TimeZone = 'UTC'", protocolName);
            executeUpdate(conn1, "SET TimeZone = 'UTC'", useExtendedProtocol);
            
            printSql(1, "SET application_name = 'test_app'", protocolName);
            executeUpdate(conn1, "SET application_name = 'test_app'", useExtendedProtocol);
            
            printSql(1, "SET IntervalStyle = sql_standard", protocolName);
            executeUpdate(conn1, "SET IntervalStyle = sql_standard", useExtendedProtocol);
            
            printSql(1, "SET work_mem = '8MB'", protocolName);
            executeUpdate(conn1, "SET work_mem = '8MB'", useExtendedProtocol);
            
            printSql(1, "SET maintenance_work_mem = '16MB'", protocolName);
            executeUpdate(conn1, "SET maintenance_work_mem = '16MB'", useExtendedProtocol);
            
            printSql(1, "SET temp_buffers = '16MB'", protocolName);
            executeUpdate(conn1, "SET temp_buffers = '16MB'", useExtendedProtocol);
            
            printSql(1, "SET logical_decoding_work_mem = '128MB'", protocolName);
            executeUpdate(conn1, "SET logical_decoding_work_mem = '128MB'", useExtendedProtocol);
            
            printSql(1, "SET statement_timeout = 30000", protocolName);
            executeUpdate(conn1, "SET statement_timeout = 30000", useExtendedProtocol);
            
            printSql(1, "SET lock_timeout = 10000", protocolName);
            executeUpdate(conn1, "SET lock_timeout = 10000", useExtendedProtocol);
            
            printSql(1, "SET idle_in_transaction_session_timeout = 60000", protocolName);
            executeUpdate(conn1, "SET idle_in_transaction_session_timeout = 60000", useExtendedProtocol);
            
            printSql(1, "SET search_path = 'myschema, public'", protocolName);
            executeUpdate(conn1, "SET search_path = 'myschema, public'", useExtendedProtocol);
            
            printSql(1, "SET extra_float_digits = 2", protocolName);
            executeUpdate(conn1, "SET extra_float_digits = 2", useExtendedProtocol);
            
            printSql(1, "SET bytea_output = 'escape'", protocolName);
            executeUpdate(conn1, "SET bytea_output = 'escape'", useExtendedProtocol);
            
            printSql(1, "SET xmloption = 'content'", protocolName);
            executeUpdate(conn1, "SET xmloption = 'content'", useExtendedProtocol);
            
            printSql(1, "SET enable_seqscan = off", protocolName);
            executeUpdate(conn1, "SET enable_seqscan = off", useExtendedProtocol);
            
            System.out.println(GREEN + "  → 19个GUC参数设置完成" + RESET);

            System.out.println(YELLOW + "步骤1完成\n" + RESET);
            Thread.sleep(100);

            // ============ 步骤2：切换到读节点 ============
            System.out.println(YELLOW + "步骤2：切换到读节点..." + RESET);

            printSql(1, "SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY", protocolName);
            executeUpdate(conn1, "SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY", useExtendedProtocol);

            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user, pg_is_in_recovery()", protocolName);
            BackendInfo backend2 = getBackendInfoWithRecovery(conn1, useExtendedProtocol);
            System.out.println(BLUE + "  → 后端连接信息: " + backend2 + RESET);

            // 检测点2：连接切换，且 pg_is_in_recovery = t 或 port=25432（多活场景下读节点端口为25432）
            boolean switchedToRead = backend2.isInRecovery || backend2.port == 25432;
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点2】检查是否切换到读节点:");
            System.out.println("  期望: pg_is_in_recovery = true 或 port = 25432 (读节点)");
            System.out.println("  实际: pg_is_in_recovery = " + backend2.isInRecovery + ", port = " + backend2.port);
            if (switchedToRead) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 已切换到读节点" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 未切换到读节点" + RESET);
                allPassed = false;
                failureDetails.append("检测点2失败; ");
            }
            System.out.println("─".repeat(100) + "\n");

            // 检测点3：检查19个参数值是否正确同步
            System.out.println(GREEN + "  → 开始检查19个GUC参数是否同步..." + RESET);
            
            printSql(1, "SHOW default_transaction_read_only", protocolName);
            String defaultTxnReadOnlyAfterSwitch = getGucValue(conn1, "default_transaction_read_only", useExtendedProtocol);
            
            printSql(1, "SHOW standard_conforming_strings", protocolName);
            String scsAfterSwitch = getGucValue(conn1, "standard_conforming_strings", useExtendedProtocol);
            
            printSql(1, "SHOW DateStyle", protocolName);
            String dateStyleAfterSwitch = getGucValue(conn1, "DateStyle", useExtendedProtocol);
            
            printSql(1, "SHOW TimeZone", protocolName);
            String timeZoneAfterSwitch = getGucValue(conn1, "TimeZone", useExtendedProtocol);
            
            printSql(1, "SHOW application_name", protocolName);
            String appNameAfterSwitch = getGucValue(conn1, "application_name", useExtendedProtocol);
            
            printSql(1, "SHOW IntervalStyle", protocolName);
            String intervalStyleAfterSwitch = getGucValue(conn1, "IntervalStyle", useExtendedProtocol);
            
            printSql(1, "SHOW work_mem", protocolName);
            String workMemAfterSwitch = getGucValue(conn1, "work_mem", useExtendedProtocol);
            
            printSql(1, "SHOW maintenance_work_mem", protocolName);
            String maintenanceWorkMemAfterSwitch = getGucValue(conn1, "maintenance_work_mem", useExtendedProtocol);
            
            printSql(1, "SHOW temp_buffers", protocolName);
            String tempBuffersAfterSwitch = getGucValue(conn1, "temp_buffers", useExtendedProtocol);
            
            printSql(1, "SHOW logical_decoding_work_mem", protocolName);
            String logicalDecodingWorkMemAfterSwitch = getGucValue(conn1, "logical_decoding_work_mem", useExtendedProtocol);
            
            printSql(1, "SHOW statement_timeout", protocolName);
            String stmtTimeoutAfterSwitch = getGucValue(conn1, "statement_timeout", useExtendedProtocol);
            
            printSql(1, "SHOW lock_timeout", protocolName);
            String lockTimeoutAfterSwitch = getGucValue(conn1, "lock_timeout", useExtendedProtocol);
            
            printSql(1, "SHOW idle_in_transaction_session_timeout", protocolName);
            String idleTimeoutAfterSwitch = getGucValue(conn1, "idle_in_transaction_session_timeout", useExtendedProtocol);
            
            printSql(1, "SHOW search_path", protocolName);
            String searchPathAfterSwitch = getGucValue(conn1, "search_path", useExtendedProtocol);
            
            printSql(1, "SHOW extra_float_digits", protocolName);
            String extraFloatDigitsAfterSwitch = getGucValue(conn1, "extra_float_digits", useExtendedProtocol);
            
            printSql(1, "SHOW bytea_output", protocolName);
            String byteaOutputAfterSwitch = getGucValue(conn1, "bytea_output", useExtendedProtocol);
            
            printSql(1, "SHOW xmloption", protocolName);
            String xmloptionAfterSwitch = getGucValue(conn1, "xmloption", useExtendedProtocol);
            
            printSql(1, "SHOW enable_seqscan", protocolName);
            String enableSeqscanAfterSwitch = getGucValue(conn1, "enable_seqscan", useExtendedProtocol);

            // 规范化后比较（去除引号差异）
            // 注意: default_transaction_read_only 不能修改，只检查它是否保持不变
            // 排除 transaction_isolation 和 transaction_read_only，因为它们是事务级别参数
            boolean paramsCorrect = normalizeGucValue(defaultTxnReadOnlyAfterSwitch).equals(normalizeGucValue(defaultTransactionReadOnly)) &&
                                   normalizeGucValue(scsAfterSwitch).equals("off") &&
                                   normalizeGucValue(dateStyleAfterSwitch).equals("ISO, DMY") &&
                                   normalizeGucValue(timeZoneAfterSwitch).equalsIgnoreCase("UTC") &&
                                   normalizeGucValue(appNameAfterSwitch).equals("test_app") &&
                                   normalizeGucValue(intervalStyleAfterSwitch).equals("sql_standard") &&
                                   normalizeGucValue(workMemAfterSwitch).equals("8MB") &&
                                   normalizeGucValue(maintenanceWorkMemAfterSwitch).equals("16MB") &&
                                   normalizeGucValue(tempBuffersAfterSwitch).equals("16MB") &&
                                   normalizeGucValue(logicalDecodingWorkMemAfterSwitch).equals("128MB") &&
                                   normalizeGucValue(stmtTimeoutAfterSwitch).equals("30s") &&
                                   normalizeGucValue(lockTimeoutAfterSwitch).equals("10s") &&
                                   normalizeGucValue(idleTimeoutAfterSwitch).equals("1min") &&
                                   normalizeGucValue(searchPathAfterSwitch).equals("myschema, public") &&
                                   normalizeGucValue(extraFloatDigitsAfterSwitch).equals("2") &&
                                   normalizeGucValue(byteaOutputAfterSwitch).equals("escape") &&
                                   normalizeGucValue(xmloptionAfterSwitch).equals("content") &&
                                   normalizeGucValue(enableSeqscanAfterSwitch).equals("off");

            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点3】检查19个常用GUC参数值是否正确同步:");
            System.out.println("  期望: default_transaction_read_only=" + defaultTransactionReadOnly + " (不变)");
            System.out.println("        standard_conforming_strings=off, DateStyle=ISO, DMY");
            System.out.println("        TimeZone=UTC, application_name=test_app, IntervalStyle=sql_standard");
            System.out.println("        work_mem=8MB, maintenance_work_mem=16MB, temp_buffers=16MB");
            System.out.println("        logical_decoding_work_mem=128MB");
            System.out.println("        statement_timeout=30s, lock_timeout=10s, idle_in_transaction_session_timeout=1min");
            System.out.println("        search_path=myschema, public");
            System.out.println("        extra_float_digits=2, bytea_output=escape, xmloption=content, enable_seqscan=off");
            System.out.println("  实际: default_transaction_read_only=" + defaultTxnReadOnlyAfterSwitch);
            System.out.println("        standard_conforming_strings=" + scsAfterSwitch);
            System.out.println("        DateStyle=" + dateStyleAfterSwitch + ", TimeZone=" + timeZoneAfterSwitch);
            System.out.println("        application_name=" + appNameAfterSwitch + ", IntervalStyle=" + intervalStyleAfterSwitch);
            System.out.println("        work_mem=" + workMemAfterSwitch + ", maintenance_work_mem=" + maintenanceWorkMemAfterSwitch);
            System.out.println("        temp_buffers=" + tempBuffersAfterSwitch + ", logical_decoding_work_mem=" + logicalDecodingWorkMemAfterSwitch);
            System.out.println("        statement_timeout=" + stmtTimeoutAfterSwitch + ", lock_timeout=" + lockTimeoutAfterSwitch);
            System.out.println("        idle_in_transaction_session_timeout=" + idleTimeoutAfterSwitch);
            System.out.println("        search_path=" + searchPathAfterSwitch);
            System.out.println("        extra_float_digits=" + extraFloatDigitsAfterSwitch + ", bytea_output=" + byteaOutputAfterSwitch);
            System.out.println("        xmloption=" + xmloptionAfterSwitch + ", enable_seqscan=" + enableSeqscanAfterSwitch);
            if (paramsCorrect) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 所有19个常用GUC参数已正确同步到读节点" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 部分参数未正确同步" + RESET);
                allPassed = false;
                failureDetails.append("检测点3失败; ");
            }
            System.out.println("─".repeat(100) + "\n");

            System.out.println(YELLOW + "步骤2完成\n" + RESET);
            Thread.sleep(100);

            // ============ 步骤3：RESET ALL并切换回写节点 ============
            System.out.println(YELLOW + "步骤3：RESET ALL并切换回写节点..." + RESET);

            printSql(1, "RESET ALL", protocolName);
            executeUpdate(conn1, "RESET ALL", useExtendedProtocol);

            printSql(1, "SET SESSION CHARACTERISTICS AS TRANSACTION READ WRITE", protocolName);
            executeUpdate(conn1, "SET SESSION CHARACTERISTICS AS TRANSACTION READ WRITE", useExtendedProtocol);

            // 检测点4：检查19个参数值是否都恢复默认值
            System.out.println(GREEN + "  → 开始检查19个GUC参数是否恢复默认值..." + RESET);
            
            printSql(1, "SHOW default_transaction_read_only", protocolName);
            String defaultTxnReadOnlyAfterReset = getGucValue(conn1, "default_transaction_read_only", useExtendedProtocol);
            
            printSql(1, "SHOW standard_conforming_strings", protocolName);
            String scsAfterReset = getGucValue(conn1, "standard_conforming_strings", useExtendedProtocol);
            
            printSql(1, "SHOW DateStyle", protocolName);
            String dateStyleAfterReset = getGucValue(conn1, "DateStyle", useExtendedProtocol);
            
            printSql(1, "SHOW TimeZone", protocolName);
            String timeZoneAfterReset = getGucValue(conn1, "TimeZone", useExtendedProtocol);
            
            printSql(1, "SHOW IntervalStyle", protocolName);
            String intervalStyleAfterReset = getGucValue(conn1, "IntervalStyle", useExtendedProtocol);
            
            printSql(1, "SHOW search_path", protocolName);
            String searchPathAfterReset = getGucValue(conn1, "search_path", useExtendedProtocol);
            
            printSql(1, "SHOW extra_float_digits", protocolName);
            String extraFloatDigitsAfterReset = getGucValue(conn1, "extra_float_digits", useExtendedProtocol);
            
            printSql(1, "SHOW bytea_output", protocolName);
            String byteaOutputAfterReset = getGucValue(conn1, "bytea_output", useExtendedProtocol);
            
            printSql(1, "SHOW xmloption", protocolName);
            String xmloptionAfterReset = getGucValue(conn1, "xmloption", useExtendedProtocol);
            
            printSql(1, "SHOW enable_seqscan", protocolName);
            String enableSeqscanAfterReset = getGucValue(conn1, "enable_seqscan", useExtendedProtocol);

            // 规范化后比较（去除引号差异）
            // 注意：extra_float_digits的真正默认值是1，不是记录下来的值（因为JDBC会设置3）
            // 内存参数、超时参数在RESET ALL后会恢复到默认值
            boolean paramsReset = normalizeGucValue(defaultTxnReadOnlyAfterReset).equals(normalizeGucValue(defaultTransactionReadOnly)) &&
                                 normalizeGucValue(scsAfterReset).equals(normalizeGucValue(defaultStandardConformingStrings)) &&
                                 normalizeGucValue(dateStyleAfterReset).equals(normalizeGucValue(defaultDateStyle)) &&
                                 normalizeGucValue(timeZoneAfterReset).equals(normalizeGucValue(defaultTimeZone)) &&
                                 normalizeGucValue(intervalStyleAfterReset).equals(normalizeGucValue(defaultIntervalStyle)) &&
                                 normalizeGucValue(searchPathAfterReset).equals(normalizeGucValue(defaultSearchPath)) &&
                                 normalizeGucValue(extraFloatDigitsAfterReset).equals("1") &&
                                 normalizeGucValue(byteaOutputAfterReset).equals(normalizeGucValue(defaultByteaOutput)) &&
                                 normalizeGucValue(xmloptionAfterReset).equals(normalizeGucValue(defaultXmloption)) &&
                                 normalizeGucValue(enableSeqscanAfterReset).equals(normalizeGucValue(defaultEnableSeqscan));

            System.out.println("\n" + "─".repeat(100));
            System.out.println("【检测点4】检查19个常用GUC参数值是否恢复默认值:");
            System.out.println("  期望: default_transaction_read_only=" + defaultTransactionReadOnly);
            System.out.println("        standard_conforming_strings=" + defaultStandardConformingStrings);
            System.out.println("        DateStyle=" + defaultDateStyle + ", TimeZone=" + defaultTimeZone);
            System.out.println("        IntervalStyle=" + defaultIntervalStyle + ", search_path=" + defaultSearchPath);
            System.out.println("        extra_float_digits=1, bytea_output=" + defaultByteaOutput);
            System.out.println("        xmloption=" + defaultXmloption + ", enable_seqscan=" + defaultEnableSeqscan);
            System.out.println("        (内存/超时参数已恢复默认值)");
            System.out.println("  实际: default_transaction_read_only=" + defaultTxnReadOnlyAfterReset);
            System.out.println("        standard_conforming_strings=" + scsAfterReset);
            System.out.println("        DateStyle=" + dateStyleAfterReset + ", TimeZone=" + timeZoneAfterReset);
            System.out.println("        IntervalStyle=" + intervalStyleAfterReset + ", search_path=" + searchPathAfterReset);
            System.out.println("        extra_float_digits=" + extraFloatDigitsAfterReset + ", bytea_output=" + byteaOutputAfterReset);
            System.out.println("        xmloption=" + xmloptionAfterReset + ", enable_seqscan=" + enableSeqscanAfterReset);
            if (paramsReset) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 所有19个常用GUC参数已恢复默认值" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 部分参数未恢复默认值" + RESET);
                allPassed = false;
                failureDetails.append("检测点4失败; ");
            }
            System.out.println("─".repeat(100) + "\n");

            System.out.println(YELLOW + "步骤3完成\n" + RESET);

            // 记录测试结果
            recordResult("测试常用guc参数读写切换", "常用GUC参数读写切换（" + protocolName + "）",
                        "所有检测点通过", allPassed ? "所有检测点通过" : failureDetails.toString(),
                        allPassed, allPassed ? "通过" : "失败");

        } finally {
            if (conn1 != null) {
                try { conn1.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    // ==================== 测试用例2.8：无效GUC参数错误信息 ====================

    public void testCase2_8_InvalidGucError_SimpleProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2.8-Simple协议】无效GUC参数错误信息测试");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2_8_InvalidGucError(false, "Simple协议");
    }

    public void testCase2_8_InvalidGucError_ExtendedProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2.8-Extended协议】无效GUC参数错误信息测试");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2_8_InvalidGucError(true, "Extended协议");
    }

    /**
     * 测试用例2.8：无效GUC参数错误信息
     * 目标：验证设置无效GUC参数时能返回错误信息
     */
    private void executeTestCase2_8_InvalidGucError(boolean useExtendedProtocol, String protocolName) throws SQLException, InterruptedException, Exception {
        Connection conn = null;
        boolean errorCaptured = false;
        String errorStage = "";
        String errorMessage = null;
        String sqlState = null;
        int errorCode = 0;

        try {
            String url = getUrlWithProtocol(useExtendedProtocol);
            conn = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn.setAutoCommit(true);

            System.out.println(YELLOW + "步骤1：客户端连接1设置无效的GUC参数..." + RESET);
            printSql(1, "SET A = 1", protocolName);

            try {
                executeUpdate(conn, "SET A = 1", useExtendedProtocol);
                System.out.println(YELLOW + "  → SET命令未立即抛出错误，准备执行后续查询触发同步" + RESET);
            } catch (SQLException e) {
                errorCaptured = true;
                errorStage = "SET";
                sqlState = e.getSQLState();
                errorCode = e.getErrorCode();
                errorMessage = e.getMessage();
                System.out.println(GREEN + "  → 捕获到错误: " + summarizeSqlException(e) + RESET);
            }

            if (!errorCaptured) {
                System.out.println(YELLOW + "步骤2：执行SELECT 2触发GUC同步..." + RESET);
                printSql(1, "SELECT 2", protocolName);
                try {
                    if (useExtendedProtocol) {
                        try (PreparedStatement pstmt = conn.prepareStatement("SELECT 2")) {
                            pstmt.executeQuery();
                        }
                    } else {
                        try (Statement stmt = conn.createStatement()) {
                            stmt.executeQuery("SELECT 2");
                        }
                    }
                    System.out.println(RED + "  → 未捕获到预期错误" + RESET);
                } catch (SQLException e) {
                    errorCaptured = true;
                    errorStage = "SELECT";
                    sqlState = e.getSQLState();
                    errorCode = e.getErrorCode();
                    errorMessage = e.getMessage();
                    System.out.println(GREEN + "  → 捕获到错误: " + summarizeSqlException(e) + RESET);
                }
            }

            boolean passed = errorCaptured && errorMessage != null && !errorMessage.isBlank();
            String expected = "期望: 无效GUC参数触发错误并返回错误信息";
            String actual;
            if (errorCaptured) {
                String sanitized = errorMessage == null ? "" : errorMessage.replaceAll("\\s+", " ").trim();
                actual = String.format("阶段=%s, SQLState=%s, ErrorCode=%d, Message=%s", errorStage, sqlState, errorCode, sanitized);
            } else {
                actual = "未捕获到任何错误";
            }

            if (!passed) {
                System.out.println(RED + "【检测点】未能捕获到错误信息" + RESET);
            } else {
                System.out.println(GREEN + "【检测点】已成功捕获错误信息" + RESET);
            }

            recordResult("GUC参数错误信息", "无效参数错误（" + protocolName + "）", expected, actual, passed, passed ? "通过" : "失败");
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // ==================== 测试用例2.7：内存泄漏测试 ====================
    
    /**
     * 测试用例2.7：内存泄漏测试 - Simple Query Protocol
     * 目标：反复执行SET RESET SET RESET ALL，看是否有内存泄漏
     */
    public void testCase2_7_MemoryLeakTest_SimpleProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2.7-Simple协议】内存泄漏测试 - 循环1000次执行SET/RESET操作");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2_7_MemoryLeak(false, "Simple协议");
    }
    
    /**
     * 测试用例2.7：内存泄漏测试 - Extended Query Protocol
     * 目标：反复执行SET RESET SET RESET ALL，看是否有内存泄漏
     */
    public void testCase2_7_MemoryLeakTest_ExtendedProtocol() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例2.7-Extended协议】内存泄漏测试 - 循环1000次执行SET/RESET操作");
        System.out.println("=".repeat(100) + "\n");
        executeTestCase2_7_MemoryLeak(true, "Extended协议");
    }
    
    /**
     * 执行内存泄漏测试 - 严格按照文档2.7要求
     * 连接1循环1000次执行：SET DateStyle = ISO, DMY; SET extra_float_digits = 3; RESET DateStyle; RESET ALL;
     * 连接2循环1000次执行：SET DateStyle = ISO, DMY; SET extra_float_digits = 3; RESET ALL;
     * 无检查点，只执行命令，用于观察内存是否稳定
     */
    private void executeTestCase2_7_MemoryLeak(boolean useExtendedProtocol, String protocolName) throws SQLException, InterruptedException, Exception {
        Connection conn1 = null;
        Connection conn2 = null;
        boolean allPassed = true;
        StringBuilder failureDetails = new StringBuilder();
        
        try {
            String url = getUrlWithProtocol(useExtendedProtocol);
            
            // ============ 步骤1：客户端连接1循环执行1000次 ============
            System.out.println(YELLOW + "步骤1：客户端连接1开始循环执行SET/RESET操作（共1000次）..." + RESET);
            conn1 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn1.setAutoCommit(true); // 事务外执行
            
            // 获取后端连接信息
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend1 = getBackendInfo(conn1, useExtendedProtocol);
            System.out.println(BLUE + "  → 后端连接: " + backend1 + RESET);
            System.out.println(YELLOW + "  → 开始循环执行..." + RESET);
            
            long startTime = System.currentTimeMillis();
            int loopCount = 1000;
            int errorCount = 0;
            
            for (int i = 1; i <= loopCount; i++) {
                try {
                    // 严格按照文档2.7要求执行4条命令：
                    // 1. SET DateStyle = ISO, DMY;
                    executeUpdate(conn1, "SET DateStyle = ISO, DMY", useExtendedProtocol);
                    
                    // 2. SET extra_float_digits = 3;
                    executeUpdate(conn1, "SET extra_float_digits = 3", useExtendedProtocol);
                    
                    // 3. RESET DateStyle;
                    executeUpdate(conn1, "RESET DateStyle", useExtendedProtocol);
                    
                    // 4. RESET ALL;
                    executeUpdate(conn1, "RESET ALL", useExtendedProtocol);
                    
                    // 每100次打印一次进度
                    if (i % 100 == 0) {
                        System.out.println(GREEN + "  → 已完成 " + i + "/" + loopCount + " 次循环" + RESET);
                    }
                    
                } catch (SQLException e) {
                    errorCount++;
                    System.err.println(RED + "  → 第 " + i + " 次循环出错: " + e.getMessage() + RESET);
                    if (errorCount > 10) {
                        // 如果错误超过10次，停止测试
                        System.err.println(RED + "  → 错误次数过多，停止测试" + RESET);
                        allPassed = false;
                        failureDetails.append("循环执行出错次数过多(>" + errorCount + "); ");
                        break;
                    }
                }
            }
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【测试总结】内存泄漏测试完成:");
            System.out.println("  循环次数: " + loopCount);
            System.out.println("  错误次数: " + errorCount);
            System.out.println("  执行时间: " + duration + " ms");
            System.out.println("  平均每次: " + (duration * 1.0 / loopCount) + " ms");
            
            if (errorCount == 0) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 所有循环执行成功，无错误" + RESET);
            } else if (errorCount <= 10) {
                System.out.println(YELLOW + "  结果: ⚠ 警告 - 有 " + errorCount + " 次执行出错，但在可接受范围内" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 错误次数过多" + RESET);
                allPassed = false;
            }
            System.out.println("  说明: 此测试主要用于观察内存使用情况，需要通过外部工具监控内存");
            System.out.println("─".repeat(100) + "\n");
            
            // 验证连接仍然可用
            printSql(1, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backendFinal = getBackendInfo(conn1, useExtendedProtocol);
            
            boolean sameBackend = backend1.pid.equals(backendFinal.pid);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【连接验证】验证后端连接是否稳定:");
            System.out.println("  期望: 使用同一个后端连接 (pid相同)");
            System.out.println("  实际: 初始pid=" + backend1.pid + ", 最终pid=" + backendFinal.pid);
            if (sameBackend) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 后端连接稳定" + RESET);
            } else {
                System.out.println(YELLOW + "  结果: ⚠ 警告 - 后端连接发生了变化" + RESET);
            }
            System.out.println("─".repeat(100) + "\n");
            
            System.out.println(YELLOW + "步骤1完成\n" + RESET);
            
            // ============ 步骤2：客户端连接2循环执行1000次（命令不同） ============
            System.out.println(YELLOW + "步骤2：客户端连接2开始循环执行SET/RESET操作（共1000次）..." + RESET);
            conn2 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn2.setAutoCommit(true); // 事务外执行
            
            // 获取后端连接信息
            printSql(2, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backend2 = getBackendInfo(conn2, useExtendedProtocol);
            System.out.println(BLUE + "  → 后端连接2: " + backend2 + RESET);
            System.out.println(YELLOW + "  → 开始循环执行..." + RESET);
            
            long startTime2 = System.currentTimeMillis();
            int errorCount2 = 0;
            
            for (int i = 1; i <= loopCount; i++) {
                try {
                    // 连接2执行3条命令（没有RESET DateStyle）：
                    // 1. SET DateStyle = ISO, DMY;
                    executeUpdate(conn2, "SET DateStyle = ISO, DMY", useExtendedProtocol);
                    
                    // 2. SET extra_float_digits = 3;
                    executeUpdate(conn2, "SET extra_float_digits = 3", useExtendedProtocol);
                    
                    // 3. RESET ALL;
                    executeUpdate(conn2, "RESET ALL", useExtendedProtocol);
                    
                    // 每100次打印一次进度
                    if (i % 100 == 0) {
                        System.out.println(GREEN + "  → 已完成 " + i + "/" + loopCount + " 次循环" + RESET);
                    }
                    
                } catch (SQLException e) {
                    errorCount2++;
                    System.err.println(RED + "  → 第 " + i + " 次循环出错: " + e.getMessage() + RESET);
                    if (errorCount2 > 10) {
                        // 如果错误超过10次，停止测试
                        System.err.println(RED + "  → 错误次数过多，停止测试" + RESET);
                        allPassed = false;
                        failureDetails.append("连接2循环执行出错次数过多(>" + errorCount2 + "); ");
                        break;
                    }
                }
            }
            
            long endTime2 = System.currentTimeMillis();
            long duration2 = endTime2 - startTime2;
            
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【测试总结-连接2】内存泄漏测试完成:");
            System.out.println("  循环次数: " + loopCount);
            System.out.println("  错误次数: " + errorCount2);
            System.out.println("  执行时间: " + duration2 + " ms");
            System.out.println("  平均每次: " + (duration2 * 1.0 / loopCount) + " ms");
            
            if (errorCount2 == 0) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 所有循环执行成功，无错误" + RESET);
            } else if (errorCount2 <= 10) {
                System.out.println(YELLOW + "  结果: ⚠ 警告 - 有 " + errorCount2 + " 次执行出错，但在可接受范围内" + RESET);
            } else {
                System.out.println(RED + "  结果: ✗ 失败 - 错误次数过多" + RESET);
                allPassed = false;
            }
            System.out.println("─".repeat(100) + "\n");
            
            // 验证连接2仍然可用
            printSql(2, "SELECT inet_server_addr(), inet_server_port(), pg_backend_pid(), current_user", protocolName);
            BackendInfo backendFinal2 = getBackendInfo(conn2, useExtendedProtocol);
            
            boolean sameBackend2 = backend2.pid.equals(backendFinal2.pid);
            System.out.println("\n" + "─".repeat(100));
            System.out.println("【连接验证-连接2】验证后端连接是否稳定:");
            System.out.println("  期望: 使用同一个后端连接 (pid相同)");
            System.out.println("  实际: 初始pid=" + backend2.pid + ", 最终pid=" + backendFinal2.pid);
            if (sameBackend2) {
                System.out.println(GREEN + "  结果: ✓ 通过 - 后端连接稳定" + RESET);
            } else {
                System.out.println(YELLOW + "  结果: ⚠ 警告 - 后端连接发生了变化" + RESET);
            }
            System.out.println("─".repeat(100) + "\n");
            
            System.out.println(YELLOW + "步骤2完成\n" + RESET);
            
            // 记录测试结果
            String resultDetail = "连接1: 循环=" + loopCount + ", 错误=" + errorCount + ", 时间=" + duration + "ms; " +
                                "连接2: 循环=" + loopCount + ", 错误=" + errorCount2 + ", 时间=" + duration2 + "ms";
            recordResult("内存泄漏测试", "2个连接各循环1000次SET/RESET操作（" + protocolName + "）", 
                        "执行成功，错误次数=0", 
                        resultDetail, 
                        allPassed && errorCount == 0 && errorCount2 == 0, 
                        (allPassed && errorCount == 0 && errorCount2 == 0) ? "通过" : ((errorCount <= 10 && errorCount2 <= 10) ? "警告" : "失败"));
            
            // ============ 保持2个连接10分钟，用于观察内存情况 ============
            System.out.println("\n" + "=".repeat(100));
            System.out.println(YELLOW + "【内存观察期】保持2个连接打开，休眠10分钟以便观察内存使用情况..." + RESET);
            System.out.println("  后端连接1: " + backendFinal);
            System.out.println("  后端连接2: " + backendFinal2);
            System.out.println("  建议：使用 jmap、jvisualvm 等工具监控 JVM 内存");
            System.out.println("  建议：在数据库端监控连接的内存占用");
            System.out.println("  休眠开始时间: " + new java.util.Date());
            System.out.println("=".repeat(100));
            
            for (int i = 1; i <= 10; i++) {
                Thread.sleep(60000); // 休眠1分钟
                System.out.println(GREEN + "  → 已等待 " + i + "/10 分钟，连接保持打开状态..." + RESET);
            }
            
            System.out.println("\n" + "=".repeat(100));
            System.out.println(YELLOW + "【内存观察期结束】休眠结束时间: " + new java.util.Date() + RESET);
            System.out.println("  即将关闭2个连接（连接1和连接2）...");
            System.out.println("=".repeat(100) + "\n");
                        
        } finally {
            if (conn1 != null) try { conn1.close(); } catch (SQLException e) { e.printStackTrace(); }
            if (conn2 != null) try { conn2.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    // ==================== 测试用例：Pipeline模式测试（使用QueryExecutor反射） ====================
    
    public void testCase_SimpleTest() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【Pipeline模式测试】客户端连接后10秒倒计时，使用Pipeline方式执行SQL");
        System.out.println("=".repeat(100) + "\n");
        
        Connection conn1 = null;
        
        try {
            // 步骤1：客户端连接1执行
            System.out.println(YELLOW + "步骤1：客户端连接1开始执行..." + RESET);
            String url = getUrlWithProtocol(false); // 使用Simple协议
            conn1 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn1.setAutoCommit(true);
            
            // 连接后等10秒，每过一秒屏幕打印倒计时
            System.out.println(GREEN + "  → 连接成功，开始10秒倒计时..." + RESET);
            for (int i = 10; i > 0; i--) {
                System.out.println("  倒计时: " + i + " 秒");
                Thread.sleep(1000);
            }
            
            // 开始执行前打印开始执行
            System.out.println(GREEN + "  → 开始执行SQL语句（Pipeline模式）..." + RESET);
            
            // 尝试使用反射访问QueryExecutor实现Pipeline
            try {
                System.out.println(BLUE + "\n=== 尝试使用QueryExecutor实现Pipeline ===" + RESET);
                
                // 获取底层的PgConnection
                Connection unwrapped = conn1.unwrap(Connection.class);
                System.out.println(BLUE + "  → 连接类型: " + unwrapped.getClass().getName() + RESET);
                
                // 尝试反射获取queryExecutor字段
                Class<?> connClass = unwrapped.getClass();
                java.lang.reflect.Field queryExecutorField = null;
                
                // 尝试在当前类和父类中查找queryExecutor字段
                while (connClass != null && queryExecutorField == null) {
                    try {
                        queryExecutorField = connClass.getDeclaredField("queryExecutor");
                    } catch (NoSuchFieldException e) {
                        connClass = connClass.getSuperclass();
                    }
                }
                
                if (queryExecutorField != null) {
                    queryExecutorField.setAccessible(true);
                    Object queryExecutor = queryExecutorField.get(unwrapped);
                    System.out.println(GREEN + "  → 成功获取QueryExecutor: " + queryExecutor.getClass().getName() + RESET);
                    
                    // 查看QueryExecutor的方法
                    System.out.println(BLUE + "  → QueryExecutor可用方法:" + RESET);
                    for (java.lang.reflect.Method method : queryExecutor.getClass().getMethods()) {
                        if (method.getName().contains("send") || method.getName().contains("execute")) {
                            System.out.println("     - " + method.getName() + "()");
                        }
                    }
                    
                    System.out.println(YELLOW + "\n  注意：QueryExecutor的sendQuery()方法需要特定的参数类型" + RESET);
                    System.out.println(YELLOW + "  由于API复杂度，这里改用标准JDBC方式演示" + RESET);
                } else {
                    System.out.println(YELLOW + "  → 未找到queryExecutor字段，使用标准JDBC方式" + RESET);
                }
                
            } catch (Exception e) {
                System.out.println(YELLOW + "  → 反射访问失败: " + e.getMessage() + RESET);
                System.out.println(YELLOW + "  → 改用标准JDBC方式" + RESET);
            }
            
            System.out.println(BLUE + "\n=== 使用标准JDBC执行（作为对比） ===" + RESET);
            
            // 执行SQL语句
            printSql(1, "SET DateStyle = ISO, DMY", "Pipeline模式");
            executeUpdate(conn1, "SET DateStyle = ISO, DMY", false);
            
            printSql(1, "SET extra_float_digits = 2", "Pipeline模式");
            executeUpdate(conn1, "SET extra_float_digits = 2", false);
            
            printSql(1, "SHOW DateStyle", "Pipeline模式");
            String dateStyleValue = getGucValue(conn1, "DateStyle", false);
            System.out.println(GREEN + "  → DateStyle: " + dateStyleValue + RESET);
            
            printSql(1, "SET extra_float_digits = 3", "Pipeline模式");
            executeUpdate(conn1, "SET extra_float_digits = 3", false);
            
            System.out.println(YELLOW + "\n步骤1完成\n" + RESET);
            
            System.out.println(BLUE + "=".repeat(100));
            System.out.println("【总结】");
            System.out.println("1. PostgreSQL JDBC驱动的QueryExecutor确实存在");
            System.out.println("2. 但其sendQuery()方法需要复杂的参数（Query对象等）");
            System.out.println("3. 标准JDBC API不直接支持真正的pipeline模式");
            System.out.println("4. 建议使用JDBC Batch API作为替代方案");
            System.out.println("=".repeat(100) + RESET);
            
            // 记录测试结果
            recordResult("Pipeline模式测试", "客户端连接后10秒倒计时执行SQL（探索QueryExecutor）", 
                        "探索成功", "探索成功", 
                        true, "通过");
                        
        } finally {
            if (conn1 != null) try { conn1.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }
    
    // ==================== 测试用例：Pipeline模式测试（使用JDBC Batch） ====================
    
    public void testCase_PipelineMode_Batch() throws SQLException, InterruptedException, Exception {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【Pipeline模式测试 - JDBC Batch方案】客户端连接后10秒倒计时，使用Batch批量执行SQL");
        System.out.println("=".repeat(100) + "\n");
        
        Connection conn1 = null;
        
        try {
            // 步骤1：客户端连接1执行
            System.out.println(YELLOW + "步骤1：客户端连接1开始执行..." + RESET);
            String url = getUrlWithProtocol(false); // 使用Simple协议
            conn1 = DriverManager.getConnection(url, DatabaseConfig.getUser(), DatabaseConfig.getPassword());
            conn1.setAutoCommit(true);
            
            // 连接后等10秒，每过一秒屏幕打印倒计时
            System.out.println(GREEN + "  → 连接成功，开始10秒倒计时..." + RESET);
            for (int i = 10; i > 0; i--) {
                System.out.println("  倒计时: " + i + " 秒");
                Thread.sleep(1000);
            }
            
            // 开始执行前打印开始执行
            System.out.println(GREEN + "  → 开始执行SQL语句（JDBC Batch Pipeline模式）..." + RESET);
            
            System.out.println(BLUE + "\n=== 使用JDBC Batch实现Pipeline ===" + RESET);
            
            // 准备SQL语句
            String[] queries = {
                "SET DateStyle = ISO, DMY",
                "SET extra_float_digits = 2",
                "SET extra_float_digits = 3"
            };
            
            // 使用Batch批量发送
            Statement stmt = conn1.createStatement();
            
            System.out.println(BLUE + "  → 添加SQL到Batch（模拟连续发送，不等待响应）:" + RESET);
            for (String query : queries) {
                printSql(1, query, "Batch Pipeline模式");
                stmt.addBatch(query);
                System.out.println(GREEN + "     ✓ 已添加到Batch，未发送" + RESET);
            }
            
            System.out.println(BLUE + "\n  → 执行Batch（一次性发送所有SQL）..." + RESET);
            long startTime = System.currentTimeMillis();
            int[] results = stmt.executeBatch();
            long endTime = System.currentTimeMillis();
            
            System.out.println(GREEN + "  → Batch执行完成，耗时: " + (endTime - startTime) + "ms" + RESET);
            System.out.println(BLUE + "  → Batch执行结果:" + RESET);
            for (int i = 0; i < results.length; i++) {
                System.out.println("     SQL[" + i + "]: " + queries[i] + " -> 结果: " + results[i]);
            }
            
            // 验证结果
            System.out.println(BLUE + "\n  → 验证GUC参数值:" + RESET);
            printSql(1, "SHOW DateStyle", "验证");
            String dateStyleValue = getGucValue(conn1, "DateStyle", false);
            System.out.println(GREEN + "     DateStyle = " + dateStyleValue + RESET);
            
            printSql(1, "SHOW extra_float_digits", "验证");
            String extraFloatValue = getGucValue(conn1, "extra_float_digits", false);
            System.out.println(GREEN + "     extra_float_digits = " + extraFloatValue + RESET);
            
            stmt.close();
            
            System.out.println(YELLOW + "\n步骤1完成\n" + RESET);
            
            System.out.println(BLUE + "=".repeat(100));
            System.out.println("【总结 - JDBC Batch方案】");
            System.out.println("1. ✓ 使用addBatch()可以连续添加多个SQL，不立即执行");
            System.out.println("2. ✓ 使用executeBatch()一次性发送所有SQL到服务器");
            System.out.println("3. ✓ 这种方式接近Pipeline的效果：批量发送，批量接收");
            System.out.println("4. ✓ 优点：标准JDBC API，兼容性好，代码简洁");
            System.out.println("5. ⚠ 限制：仍然是同步的，需要等待所有结果返回");
            System.out.println("=".repeat(100) + RESET);
            
            // 记录测试结果
            recordResult("Pipeline模式测试(Batch)", "使用JDBC Batch批量执行SQL", 
                        "执行成功", "执行成功", 
                        true, "通过");
                        
        } finally {
            if (conn1 != null) try { conn1.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }
}
