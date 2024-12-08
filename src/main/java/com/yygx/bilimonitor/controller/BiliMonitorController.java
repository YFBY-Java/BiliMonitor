package com.yygx.bilimonitor.controller;


import com.yygx.bilimonitor.common.Response;
import com.yygx.bilimonitor.pojo.entity.FansNum;
import com.yygx.bilimonitor.service.IBiliMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@CrossOrigin // 允许跨域
@RestController
@RequestMapping("/BiliMonitor")
public class BiliMonitorController {

    private static final Logger log = LoggerFactory.getLogger(BiliMonitorController.class);
    @Autowired
    private IBiliMonitorService biliMonitorService;


    /**
     * 根据uid获取用户粉丝数
     * @return
     */
    @RequestMapping("/fans/{uid}")
    public Response fans(@PathVariable String uid) {
        FansNum fans = biliMonitorService.fans(uid);
        log.info("fans: {}", fans);
        return Response.success(fans);
    }

}