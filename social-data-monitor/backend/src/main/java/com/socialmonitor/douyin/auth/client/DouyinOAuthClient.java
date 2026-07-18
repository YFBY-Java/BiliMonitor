package com.socialmonitor.douyin.auth.client;

import java.util.Map;

public interface DouyinOAuthClient {

    Map<String, Object> exchangeCode(String code);

    Map<String, Object> refreshAccessToken(String refreshToken);

    Map<String, Object> renewRefreshToken(String refreshToken);

    Map<String, Object> fetchUserInfo(String accessToken, String openId);
}
