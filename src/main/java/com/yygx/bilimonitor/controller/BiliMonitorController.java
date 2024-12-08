package com.yygx.bilimonitor.controller;


import com.yygx.bilimonitor.common.Response;
import com.yygx.bilimonitor.service.IBiliMonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/BiliMonitor")
public class BiliMonitorController {

    @Autowired
    private IBiliMonitorService biliMonitorService;

    @RequestMapping("/")
    public String index() {
        return "Hello World";
    }

    @RequestMapping("/live/{uid}")
    public Response live(@PathVariable String uid) {
        return Response.success("success", "");
    }


    /**
     * 根据uid获取用户粉丝数
     * @return
     */
    @RequestMapping("/fans/{uid}")
    public Response fans(@PathVariable String uid) {
        return Response.success(biliMonitorService.fans(uid));
    }

}