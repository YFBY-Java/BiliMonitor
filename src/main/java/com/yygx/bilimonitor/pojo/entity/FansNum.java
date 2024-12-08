package com.yygx.bilimonitor.pojo.entity;


import lombok.Data;

@Data
public class FansNum {
    private String uname;
    private Integer fansNum;

    public FansNum(String uname, Integer fansNum) {
        this.uname = uname;
        this.fansNum = fansNum;
    }

    public FansNum() {
    }

}