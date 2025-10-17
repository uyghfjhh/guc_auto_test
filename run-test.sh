#!/bin/bash
# GUC 测试运行脚本

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH=$JAVA_HOME/bin:$PATH

# 编译
echo "正在编译..."
mvn clean test-compile

# 运行测试
echo "正在运行测试..."
java -cp "target/test-classes:$HOME/.m2/repository/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar" \
     com.fbasecman.guc.GucSyncScenarioTest
