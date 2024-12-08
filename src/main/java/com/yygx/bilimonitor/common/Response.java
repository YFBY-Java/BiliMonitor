package com.yygx.bilimonitor.common;

import lombok.Data;

@Data
public class Response {

    private int code;
    private String msg;
    private Object data;

    public Response(int code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }
    public Response(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static Response success(Object data) {
        return new Response(200, "success", data);
    }

    public static Response success(String msg, Object data) {
        return new Response(200, msg, data);
    }

    public static Response fail(String msg) {
        return new Response(400, msg);
    }

    public static Response fail(int code, String msg) {
        return new Response(code, msg);
    }

    public static Response fail(int code, String msg, Object data) {
        return new Response(code, msg, data);
    }


}