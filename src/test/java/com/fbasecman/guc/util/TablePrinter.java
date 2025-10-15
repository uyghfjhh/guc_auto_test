package com.fbasecman.guc.util;

import com.fbasecman.guc.model.TestResult;
import java.util.List;

/**
 * 表格打印工具 - 格式化输出测试结果
 */
public class TablePrinter {
    
    // ANSI 颜色代码
    private static final String RESET = "\033[0m";
    private static final String GREEN = "\033[32m";
    private static final String RED = "\033[31m";
    private static final String BLUE = "\033[34m";
    private static final String YELLOW = "\033[33m";
    private static final String CYAN = "\033[36m";
    private static final String BOLD = "\033[1m";
    
    // 列宽定义
    private static final int COL1_WIDTH = 30;  // 测试用例类别
    private static final int COL2_WIDTH = 40;  // 测试用例名称
    private static final int COL3_WIDTH = 10;  // 结果
    
    public static void printResults(List<TestResult> results) {
        // 统计
        int passed = 0;
        int failed = 0;
        
        for (TestResult result : results) {
            if (result.isPassed()) {
                passed++;
            } else {
                failed++;
            }
        }
        
        // 每行格式：║ col1(30) │ col2(40) │ col3(10) ║
        // 宽度计算：1空格 + 30 + 1空格+1│+1空格 + 40 + 1空格+1│+1空格 + 10 + 1空格 = 88
        // 边框总宽度 = 88 + 2(两边的║) = 90
        final String TOP_BORDER = "╔════════════════════════════════════════════════════════════════════════════════════════╗";
        final String MID_BORDER = "╠════════════════════════════════════════════════════════════════════════════════════════╣";
        final String BOT_BORDER = "╚════════════════════════════════════════════════════════════════════════════════════════╝";
        
        System.out.println("\n");
        System.out.println(BOLD + CYAN + TOP_BORDER + RESET);
        
        // 标题居中
        String title = "GUC参数测试结果汇总";
        int titleWidth = getDisplayWidth(title);
        int totalContentWidth = 88; // 边框内的内容宽度（不包括两边的║）
        int leftPad = (totalContentWidth - titleWidth) / 2;
        int rightPad = totalContentWidth - titleWidth - leftPad;
        
        System.out.println(BOLD + CYAN + "║" + RESET + BOLD + 
                " ".repeat(leftPad) + title + " ".repeat(rightPad) +
                BOLD + CYAN + "║" + RESET);
        
        System.out.println(BOLD + CYAN + MID_BORDER + RESET);
        
        // 表头
        System.out.println(BOLD + CYAN + "║" + RESET + BOLD + 
                " " + padString("测试用例类别", COL1_WIDTH) + " │ " + 
                padString("测试用例名称", COL2_WIDTH) + " │ " + 
                padString("结果", COL3_WIDTH) + " " + 
                BOLD + CYAN + "║" + RESET);
        
        System.out.println(BOLD + CYAN + MID_BORDER + RESET);
        
        // 打印每一行
        for (TestResult result : results) {
            String statusText = result.isPassed() ? "✓ 通过" : "✗ 失败";
            String statusColored = result.isPassed() ? GREEN + statusText + RESET : RED + statusText + RESET;
            
            String category = result.getTestCase();
            String name = result.getParameter();
            
            System.out.println(BOLD + CYAN + "║" + RESET +
                    " " + padString(category, COL1_WIDTH) + " │ " +
                    padString(name, COL2_WIDTH) + " │ " +
                    statusColored + " ".repeat(COL3_WIDTH - getDisplayWidth(statusText)) + " " +
                    BOLD + CYAN + "║" + RESET);
        }
        
        System.out.println(BOLD + CYAN + MID_BORDER + RESET);
        
        // 统计信息
        String passRate = String.format("%.2f", results.size() > 0 ? (passed * 100.0 / results.size()) : 0);
        String summary = String.format("总计: %d │ 通过: %d │ 失败: %d │ 通过率: %s%%", 
                results.size(), passed, failed, passRate);
        
        // 带颜色的统计信息
        String summaryColored = String.format("总计: " + BLUE + "%d" + RESET + " │ 通过: " + GREEN + "%d" + RESET + 
                " │ 失败: " + RED + "%d" + RESET + " │ 通过率: " + YELLOW + "%s%%" + RESET, 
                results.size(), passed, failed, passRate);
        
        int summaryWidth = getDisplayWidth(summary);
        String padding = " ".repeat(totalContentWidth - summaryWidth);
        
        System.out.println(BOLD + CYAN + "║" + RESET + summaryColored + padding + BOLD + CYAN + "║" + RESET);
        System.out.println(BOLD + CYAN + BOT_BORDER + RESET);
        System.out.println();
    }
    
    /**
     * 填充字符串到指定显示宽度（考虑全角字符占2个字符宽度）
     */
    private static String padString(String str, int displayWidth) {
        if (str == null) {
            str = "";
        }
        
        int actualWidth = getDisplayWidth(str);
        int paddingNeeded = displayWidth - actualWidth;
        
        if (paddingNeeded > 0) {
            return str + " ".repeat(paddingNeeded);
        } else if (paddingNeeded < 0) {
            // 如果超长，截断字符串
            return truncateString(str, displayWidth);
        }
        
        return str;
    }
    
    /**
     * 计算字符串的显示宽度（全角字符占2个字符宽度）
     */
    private static int getDisplayWidth(String str) {
        if (str == null) {
            return 0;
        }
        
        int width = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (isFullWidth(c)) {
                width += 2;
            } else {
                width += 1;
            }
        }
        return width;
    }
    
    /**
     * 判断字符是否为全角字符
     */
    private static boolean isFullWidth(char c) {
        // CJK统一汉字
        if (c >= 0x4E00 && c <= 0x9FFF) return true;
        // 全角ASCII、全角中英文标点、半宽片假名、半宽平假名、半宽韩文字母
        if (c >= 0xFF00 && c <= 0xFFEF) return true;
        // CJK标点符号
        if (c >= 0x3000 && c <= 0x303F) return true;
        // 其他常见全角字符
        if (c >= 0x2E80 && c <= 0x2EFF) return true; // CJK 部首补充
        if (c >= 0x3400 && c <= 0x4DBF) return true; // CJK统一汉字扩展A
        
        return false;
    }
    
    /**
     * 截断字符串到指定显示宽度
     */
    private static String truncateString(String str, int maxWidth) {
        if (str == null) return "";
        
        int width = 0;
        int index = 0;
        
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            int charWidth = isFullWidth(c) ? 2 : 1;
            
            if (width + charWidth <= maxWidth) {
                width += charWidth;
                index = i + 1;
            } else {
                break;
            }
        }
        
        return str.substring(0, index);
    }
}

