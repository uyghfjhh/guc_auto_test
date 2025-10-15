package com.fbasecman.guc.util;

import com.fbasecman.guc.model.TestResult;
import java.util.List;

/**
 * 表格打印工具 - 格式化输出测试结果
 */
public class TablePrinter {
    
    public static void printResults(List<TestResult> results) {
        System.out.println("\n" + "=".repeat(120));
        System.out.println("GUC参数测试结果汇总");
        System.out.println("=".repeat(120));
        
        // 表头
        System.out.printf("%-30s %-25s %-20s %-20s %-10s %-30s%n",
                "测试用例", "参数名", "期望值", "实际值", "结果", "备注");
        System.out.println("-".repeat(120));
        
        // 统计
        int passed = 0;
        int failed = 0;
        
        // 打印每一行
        for (TestResult result : results) {
            String status = result.isPassed() ? "✓ 通过" : "✗ 失败";
            System.out.printf("%-30s %-25s %-20s %-20s %-10s %-30s%n",
                    result.getTestCase(),
                    result.getParameter(),
                    result.getExpectedValue(),
                    result.getActualValue(),
                    status,
                    result.getRemark());
            
            if (result.isPassed()) {
                passed++;
            } else {
                failed++;
            }
        }
        
        System.out.println("=".repeat(120));
        System.out.printf("总计: %d | 通过: %d | 失败: %d | 通过率: %.2f%%%n",
                results.size(), passed, failed, 
                results.size() > 0 ? (passed * 100.0 / results.size()) : 0);
        System.out.println("=".repeat(120) + "\n");
    }
}

