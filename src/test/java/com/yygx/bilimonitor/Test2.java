package com.yygx.bilimonitor;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Test2 {




    /**
     * 发送 GET 请求并获取响应
     * @param urlString 请求的 URL 字符串
     * @return 响应的内容
     * @throws Exception
     */
    public static String sendGetRequest(String urlString) throws Exception {
        // 创建 URL 对象
        URL url = new URL(urlString);

        // 打开连接
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");  // 使用 GET 请求
        connection.setConnectTimeout(5000);  // 设置连接超时为 5 秒
        connection.setReadTimeout(5000);     // 设置读取超时为 5 秒

        // 获取 HTTP 响应码
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {  // 如果响应码为 200（成功）
            // 读取响应内容
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return response.toString();  // 返回响应内容
        } else {
            System.out.println("请求失败，HTTP响应码：" + responseCode);
            return null;
        }
    }

    public static String getMasterInfo(String uid) throws IOException {
        String url = "https://api.live.bilibili.com/live_user/v1/Master/info?uid=" + uid;

        HttpClient httpClient = HttpClients.createDefault();
        HttpGet request = new HttpGet(url);

        HttpResponse response = httpClient.execute(request);

        return EntityUtils.toString(response.getEntity());
    }

    public static void main(String[] args) {
        try {
            String uid = "1340190821";  // 目标用户的 mid
            String response = getMasterInfo(uid);
            System.out.println(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}