package com.fbasecman.guc;

import com.fbasecman.guc.config.DatabaseConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 测试 GucParameterList 的改进功能
 */
public class TestGucParameterList {

    public static void main(String[] args) {
        // 配置日志级别
        Logger rootLogger = Logger.getLogger(GucParameterList.class.getName());
        rootLogger.setLevel(Level.ALL);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        rootLogger.addHandler(handler);

        System.out.println("=".repeat(80));
        System.out.println("测试 GucParameterList 改进功能");
        System.out.println("=".repeat(80));
        System.out.println();

        try {
            // 显示数据库配置（DatabaseConfig在静态初始化块中自动加载）
            System.out.println("数据库连接URL: " + DatabaseConfig.getUrl());
            System.out.println();

            // 建立数据库连接
            try (Connection conn = DriverManager.getConnection(
                    DatabaseConfig.getUrl(), DatabaseConfig.getUser(), DatabaseConfig.getPassword())) {

                System.out.println("✓ 数据库连接成功");
                System.out.println();

                // 测试1: 获取10个参数
                System.out.println("测试1: 从数据库获取10个参数");
                System.out.println("-".repeat(80));
                Map<String, String[]> params = GucParameterList.getGucParametersFromDatabase(conn, 10);

                System.out.println();
                System.out.println("获取到的参数列表:");
                System.out.println();
                System.out.printf("%-35s %-20s %-20s%n", "参数名", "当前值", "测试值");
                System.out.println("-".repeat(80));

                for (Map.Entry<String, String[]> entry : params.entrySet()) {
                    String name = entry.getKey();
                    String[] values = entry.getValue();
                    String currentValue = values[0];
                    String testValue = values[1];

                    System.out.printf("%-35s %-20s %-20s%n",
                        name,
                        truncate(currentValue, 20),
                        truncate(testValue, 20));
                }

                System.out.println("-".repeat(80));
                System.out.println("总计: " + params.size() + " 个参数");
                System.out.println();

                // 测试2: 获取50个参数
                System.out.println("测试2: 从数据库获取50个参数");
                System.out.println("-".repeat(80));
                Map<String, String[]> params50 = GucParameterList.getGucParametersFromDatabase(conn, 50);
                System.out.println("总计获取: " + params50.size() + " 个参数");
                System.out.println();

                // 统计参数类型
                System.out.println("测试3: 验证参数排除功能");
                System.out.println("-".repeat(80));
                boolean foundExcluded = false;
                for (String excludedParam : new String[]{
                    "session_authorization", "role", "listen_addresses",
                    "port", "max_connections", "shared_buffers"}) {
                    if (params50.containsKey(excludedParam)) {
                        System.out.println("✗ 发现不应包含的参数: " + excludedParam);
                        foundExcluded = true;
                    }
                }
                if (!foundExcluded) {
                    System.out.println("✓ 所有危险参数已被正确排除");
                }
                System.out.println();

                System.out.println("=".repeat(80));
                System.out.println("测试完成！");
                System.out.println("=".repeat(80));

            }

        } catch (Exception e) {
            System.err.println("✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String truncate(String str, int maxLength) {
        if (str == null) return "null";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}
