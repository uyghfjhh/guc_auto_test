package com.fbasecman.guc;

import com.fbasecman.guc.config.DatabaseConfig;
import com.fbasecman.guc.model.TestResult;
import com.fbasecman.guc.util.TablePrinter;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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
        
        try {
            // 使用Simple协议测试
            testCase1_NonReportParameterSync_SimpleProtocol();
            
            // 使用Extended协议测试
            testCase1_NonReportParameterSync_ExtendedProtocol();
            
        } catch (Exception e) {
            System.err.println("测试执行失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        // 打印测试结果表格
        TablePrinter.printResults(testResults);
    }
    
    /**
     * 用例1：测试非guc report参数同步 - Simple Query Protocol
     * 使用Statement执行SQL（Simple协议）
     */
    public void testCase1_NonReportParameterSync_SimpleProtocol() throws SQLException, InterruptedException {
        System.out.println("\n" + "=".repeat(100));
        System.out.println("【用例1-Simple协议】测试非guc report参数同步 (使用Statement - Simple Query Protocol)");
        System.out.println("=".repeat(100) + "\n");
        
        executeTestCase1(false, "Simple协议");
    }
    
    /**
     * 用例1：测试非guc report参数同步 - Extended Query Protocol
     * 使用PreparedStatement执行SQL（Extended协议）
     */
    public void testCase1_NonReportParameterSync_ExtendedProtocol() throws SQLException, InterruptedException {
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
    private void executeTestCase1(boolean useExtendedProtocol, String protocolName) throws SQLException, InterruptedException {
        
        Connection conn1 = null;
        Connection conn2 = null;
        
        // 记录各个检测点的结果
        boolean allPassed = true;
        StringBuilder failureDetails = new StringBuilder();
        
        try {
            // ============ 步骤1：客户端连接1执行 ============
            System.out.println(YELLOW + "步骤1：客户端连接1开始执行..." + RESET);
            conn1 = DriverManager.getConnection(
                    DatabaseConfig.getUrl(),
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
            
            System.out.println(YELLOW + "步骤1完成，保持连接1打开状态\n" + RESET);
            
            // 等待一小段时间，确保事务提交
            Thread.sleep(100);
            
            // ============ 步骤2：客户端连接2执行 ============
            System.out.println(YELLOW + "步骤2：客户端连接2开始执行..." + RESET);
            conn2 = DriverManager.getConnection(
                    DatabaseConfig.getUrl(),
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
     * 打印SQL命令（红色）
     */
    private void printSql(int clientId, String sql, String protocol) {
        System.out.println(RED + "[客户端连接" + clientId + " - " + protocol + "] SQL: " + sql + RESET);
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
     */
    private void recordResult(String testCase, String parameter, 
                             String expected, String actual, 
                             boolean passed, String remark) {
        testResults.add(new TestResult(testCase, parameter, expected, actual, passed, remark));
    }
    
    /**
     * 后端连接信息
     */
    static class BackendInfo {
        String ip;
        int port;
        String pid;
        String user;
        
        BackendInfo(String ip, int port, String pid, String user) {
            this.ip = ip;
            this.port = port;
            this.pid = pid;
            this.user = user;
        }
        
        @Override
        public String toString() {
            return String.format("ip=%s, port=%d, pid=%s, user=%s", ip, port, pid, user);
        }
    }
}

