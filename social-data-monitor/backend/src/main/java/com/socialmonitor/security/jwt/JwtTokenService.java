package com.socialmonitor.security.jwt;

import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    public String issueDevelopmentToken(String username) {
        return "dev-token-for-" + username;
    }
}

