package com.yygx.bilimonitor.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yygx.bilimonitor.common.Response;
import com.yygx.bilimonitor.pojo.entity.FansNum;
import com.yygx.bilimonitor.service.IBiliMonitorService;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class BiliMonitorServiceImpl implements IBiliMonitorService {




    /**
     * 根据用户uid获取直播数据
     * @param uid
     * @return
     */
    @Override
    public Response live(String uid) {
        return null;
    }

    @Override
    public FansNum fans(String uid) {
        String url = "https://api.live.bilibili.com/live_user/v1/Master/info?uid=" + uid;

        HttpClient httpClient = HttpClients.createDefault();
        HttpGet request = new HttpGet(url);

        HttpResponse response = null;
        try {
            response = httpClient.execute(request);
            String responseBody = EntityUtils.toString(response.getEntity());

            // 创建 ObjectMapper 实例
            ObjectMapper mapper = new ObjectMapper();
            // 解析 JSON 字符串为 JsonNode
            JsonNode rootNode = mapper.readTree(responseBody);

            // 提取 "uname" 和 "follower_num" 字段
            JsonNode dataNode = rootNode.path("data");
            String uname = dataNode.path("info").path("uname").asText();
            Integer followerNum = dataNode.path("follower_num").asInt();

            return new FansNum(uname, followerNum);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}