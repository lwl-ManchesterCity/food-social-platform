package com.imooc.commons.utils;

/**
 * 輸出 Hello 的字符串工具類
 */
public final class HelloUtil {

    /** Hello 字符串常量 */
    public static final String HELLO = "Hello";

    private HelloUtil() {
    }

    /**
     * 返回 Hello 字符串
     *
     * @return "Hello"
     */
    public static String getHello() {
        return HELLO;
    }
}
