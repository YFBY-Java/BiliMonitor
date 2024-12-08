package com.yygx.bilimonitor.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class BiliTicketUtil {

    /**
     * Convert a byte array to a hex string.
     * 
     * @param bytes The byte array to convert.
     * @return The hex string representation of the given byte array.
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * Generate a HMAC-SHA256 hash of the given message string using the given key
     * string.
     * 
     * @param key     The key string to use for the HMAC-SHA256 hash.
     * @param message The message string to hash.
     * @throws Exception If an error occurs during the HMAC-SHA256 hash generation.
     * @return The HMAC-SHA256 hash of the given message string using the given key
     *         string.
     */
    public static String hmacSha256(String key, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    /**
     * Get a Bilibili web ticket for the given CSRF token.
     * 
     * @param csrf The CSRF token to use for the web ticket, can be {@code null} or
     *             empty.
     * @return The Bilibili web ticket raw response for the given CSRF token.
     * @throws Exception If an error occurs during the web ticket generation.
     * @see https://github.com/SocialSisterYi/bilibili-API-collect/blob/master/docs/misc/sign/bili_ticket.md
     */
    public static String getBiliTicket(String csrf) throws Exception {
        // params
        long ts = System.currentTimeMillis() / 1000;
        String hexSign = hmacSha256("XgwSnGZ1p", "ts" + ts);
        StringBuilder url = new StringBuilder(
                "https://api.bilibili.com/bapis/bilibili.api.ticket.v1.Ticket/GenWebTicket");
        url.append('?');
        url.append("key_id=ec02").append('&');
        url.append("hexsign=").append(hexSign).append('&');
        url.append("context[ts]=").append(ts).append('&');
        url.append("csrf=").append(csrf == null ? "" : csrf);
        // request
        HttpURLConnection conn = (HttpURLConnection) new URI(url.toString()).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.addRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/115.0");
        InputStream in = conn.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            out.write(b);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }



    public static Map<String, String> getImgSub(String biliTicket) throws JsonProcessingException {
        Map<String, String> result = new HashMap<>();
        try {
            // 创建 ObjectMapper 实例
            ObjectMapper mapper = new ObjectMapper();

            // 解析 JSON 字符串为 JsonNode
            JsonNode rootNode = mapper.readTree(biliTicket);

            // 尝试提取 "nav" 或 "wbi_img" 节点
            JsonNode imgNode = rootNode.path("data").path("nav").path("img");
            JsonNode subNode = rootNode.path("data").path("nav").path("sub");
            if (imgNode.isMissingNode() || subNode.isMissingNode()) {
                // 如果没有找到 "nav" 节点，则尝试从 "wbi_img" 获取
                imgNode = rootNode.path("data").path("wbi_img").path("img_url");
                subNode = rootNode.path("data").path("wbi_img").path("sub_url");
            }

            // 提取 img_url 和 sub_url
            String imgUrl = imgNode.asText();
            String subUrl = subNode.asText();

            // 从 URL 中提取出需要的部分（截取URL中的标识符部分）
            String imgId = extractImgId(imgUrl);
            String subId = extractImgId(subUrl);

            // 将结果放入 Map 中
            result.put("img_id", imgId);
            result.put("sub_id", subId);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 从图片 URL 中提取出标识符部分（例如：7cd084941338484aae1ad9425b84077c）
     *
     * @param url 图片 URL
     * @return 截取出来的标识符部分
     */
    private static String extractImgId(String url) {
        // 假设 URL 以 "/bfs/wbi/" 开头，并且 ID 部分是 / 和 . 之间的字符串
        int start = url.lastIndexOf('/') + 1;
        int end = url.lastIndexOf('.');
        return end != -1 ? url.substring(start, end) : "";
    }



    /**
     * Main method to test the BiliTicketDemo class.
     * 
     * @param args The command line arguments (not used).
     */
    public static void main(String[] args) {
        try {
            String biliTicket = getBiliTicket("");
            System.out.println(biliTicket); // use empty CSRF here

            Map<String, String> imgSub = getImgSub(biliTicket);
            System.out.println(imgSub);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}