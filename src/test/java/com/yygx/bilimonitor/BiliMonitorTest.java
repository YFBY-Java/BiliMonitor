package com.yygx.bilimonitor;


import com.yygx.bilimonitor.util.BiliTicketUtil;
import com.yygx.bilimonitor.util.BilibiliWbi;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.yygx.bilimonitor.util.BiliTicketUtil.getImgSub;

public class BiliMonitorTest {

    private static final String API_URL = "https://api.bilibili.com/x/space/wbi/acc/info/";

    /**
     * 对 URL 参数进行编码
     * @param param 参数值
     * @return 编码后的参数值
     */
    public static String encodeParameter(String param) {
        try {
            return java.net.URLEncoder.encode(param, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            e.printStackTrace();
            return param;  // 如果编码失败，则返回原始参数
        }
    }

    /**
     * 构建请求 URL，确保所有参数都经过 URL 编码
     * @param params 请求参数
     * @return 编码后的完整 URL
     */
    public static String buildRequestUrl(Map<String, String> params) {
        StringBuilder urlString = new StringBuilder(API_URL);
        urlString.append("?mid=").append(encodeParameter(params.get("mid")));
        urlString.append("&token=").append(encodeParameter(params.get("token")));
        urlString.append("&platform=").append(encodeParameter(params.get("platform")));
        urlString.append("&web_location=").append(encodeParameter(params.get("web_location")));
        urlString.append("&dm_img_list=").append(encodeParameter(params.get("dm_img_list")));
        urlString.append("&dm_img_str=").append(encodeParameter(params.get("dm_img_str")));
        urlString.append("&dm_cover_img_str=").append(encodeParameter(params.get("dm_cover_img_str")));
        urlString.append("&dm_img_inter=").append(encodeParameter(params.get("dm_img_inter")));
        urlString.append("&w_webid=").append(encodeParameter(params.get("w_webid")));
        urlString.append("&w_rid=").append(encodeParameter(params.get("w_rid"))); // 由 WbiUtil 生成
        urlString.append("&wts=").append(encodeParameter(params.get("wts"))); // 由 WbiUtil 生成


        return urlString.toString();
    }

    /**
     * 获取 B站用户信息
     * @param params 请求参数
     * @return 用户信息的 JSON 字符串
     * @throws Exception
     */
    public static String getUserInfo(Map<String, String> params) throws Exception {

        // 构建 URL
        String urlString = buildRequestUrl(params);

//        System.out.println(urlString);

        // 发送请求并获取响应
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(urlString).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        // 模拟浏览器的请求头
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        connection.setRequestProperty("Accept", "application/json, text/plain, */*");
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("Host", "api.bilibili.com");
        connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
        connection.setRequestProperty("Cache-Control", "max-age=0");
        // 设置cookie
        connection.setRequestProperty("Cookie", "");


        // 检查返回的 HTTP 响应码
        int responseCode = connection.getResponseCode();
        if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
            // 获取响应结果
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return response.toString();  // 返回用户信息的 JSON 字符串
        } else {
            System.out.println("请求失败，HTTP响应码：" + responseCode);
            return null;
        }
    }

    // getBiliTicket
    private static String getBiliTicket(String cookie) throws Exception {
        String biliJct = getBiliJct(cookie);
        return BiliTicketUtil.getBiliTicket(biliJct);
    }


    public static String getBiliJct(String cookieString) {
        // 使用正则表达式匹配 "bili_jct=" 后面的值，直到遇到分号（;）
        Pattern pattern = Pattern.compile("bili_jct=([^;]+)");
        Matcher matcher = pattern.matcher(cookieString);

        // 如果找到匹配的值
        if (matcher.find()) {
            return matcher.group(1); // 返回匹配的值
        } else {
            return null; // 如果没有找到，返回 null
        }
    }




    public static void main(String[] args) {
        try {
            // 示例：设置 B站用户信息请求参数
            Map<String, String> params = new HashMap<>();
            params.put("mid", "1340190821");  // B站用户ID
            params.put("token", "");  // Token，可以为空
            params.put("platform", "web");  // 请求平台
            params.put("web_location", "1550101");  // 网站位置
//            params.put("dm_img_list", "");  // 动态图片列表
//            params.put("dm_img_str", "");  // 动态图片字符串
//            params.put("dm_cover_img_str", "");  // 封面图字符串
//            params.put("dm_img_inter", "");  // 动态图片交互



            String biliTicket = getBiliTicket("");


            Map<String, String> imgSub = getImgSub(biliTicket);

            String mixinKey = BilibiliWbi.getMixinKey(imgSub.get("img_id"), imgSub.get("sub_id"));


            // 用TreeMap自动排序
            TreeMap<String, Object> map = new TreeMap<>();
            map.put("mid", "1340190821");  // B站用户ID

            long wts = System.currentTimeMillis() / 1000;
            map.put("wts", wts);
            String param = map.entrySet().stream()
                    .map(it -> String.format("%s=%s", it.getKey(), BilibiliWbi.encodeURIComponent(it.getValue())))
                    .collect(Collectors.joining("&"));
            String s = param + mixinKey;

            String wbiSign = BilibiliWbi.md5(s);
//            System.out.println(wbiSign);

            params.put("w_rid", wbiSign);
            params.put("wts", String.valueOf(wts));


            // 获取用户信息
            String userInfoJson = getUserInfo(params);
            if (userInfoJson != null) {
                System.out.println("B站用户信息: " + userInfoJson);
            } else {
                System.out.println("获取用户信息失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}