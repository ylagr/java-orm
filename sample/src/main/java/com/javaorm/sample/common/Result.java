package com.javaorm.sample.common;

import java.io.Serializable;

/**
 * 统一 RESTful API 响应体封装
 */
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private Long total;

    public Result() {
    }

    public Result(int code, String message, T data, Long total) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.total = total;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data, null);
    }

    public static <T> Result<T> success(T data, long total) {
        return new Result<>(200, "操作成功", data, total);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null, null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }
}
