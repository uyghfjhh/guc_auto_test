package com.fbasecman.guc.model;

/**
 * 测试结果记录
 */
public class TestResult {
    private String testCase;
    private String parameter;
    private String expectedValue;
    private String actualValue;
    private boolean passed;
    private String remark;
    
    public TestResult(String testCase, String parameter, String expectedValue, 
                     String actualValue, boolean passed, String remark) {
        this.testCase = testCase;
        this.parameter = parameter;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
        this.passed = passed;
        this.remark = remark;
    }
    
    public String getTestCase() { return testCase; }
    public String getParameter() { return parameter; }
    public String getExpectedValue() { return expectedValue; }
    public String getActualValue() { return actualValue; }
    public boolean isPassed() { return passed; }
    public String getRemark() { return remark; }
}

