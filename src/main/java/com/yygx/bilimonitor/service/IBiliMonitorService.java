package com.yygx.bilimonitor.service;

import com.yygx.bilimonitor.common.Response;
import com.yygx.bilimonitor.pojo.entity.FansNum;
import org.springframework.web.bind.annotation.PathVariable;

public interface IBiliMonitorService {

    Response live(String uid);


    FansNum fans(String uid);
}