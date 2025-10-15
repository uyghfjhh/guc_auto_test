package com.fbasecman.guc.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 数据库配置类 - 支持从配置文件加载DSN信息
 */
public class DatabaseConfig {
    private static final Properties props = new Properties();
    
    static {
        try (InputStream input = DatabaseConfig.class.getClassLoader()
                .getResourceAsStream("db.properties")) {
            if (input == null) {
                // 使用默认配置
                props.setProperty("db.url", "jdbc:postgresql://localhost:5432/postgres");
                props.setProperty("db.user", "postgres");
                props.setProperty("db.password", "postgres");
            } else {
                props.load(input);
            }
        } catch (IOException ex) {
            throw new RuntimeException("加载数据库配置失败", ex);
        }
    }
    
    public static String getUrl() {
        return props.getProperty("db.url");
    }
    
    public static String getUser() {
        return props.getProperty("db.user");
    }
    
    public static String getPassword() {
        return props.getProperty("db.password");
    }
}

