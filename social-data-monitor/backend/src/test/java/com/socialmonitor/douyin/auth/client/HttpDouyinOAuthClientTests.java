package com.socialmonitor.douyin.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.socialmonitor.douyin.auth.config.DouyinAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpDouyinOAuthClientTests {

    @Test
    void exchangesCodeUsingTheOfficialFormEndpointAndReturnsUnmodifiedJson() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://open.douyin.com/oauth/access_token/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", org.hamcrest.Matchers.startsWith("application/x-www-form-urlencoded")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_key=client-key")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=client-secret")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=raw-code")))
                .andRespond(withSuccess("""
                        {"data":{"access_token":"raw-access","provider_extra":"keep-me"},"message":"success"}
                        """, MediaType.APPLICATION_JSON));
        HttpDouyinOAuthClient client = new HttpDouyinOAuthClient(properties(), builder);

        var response = client.exchangeCode("raw-code");

        assertThat(((java.util.Map<?, ?>) response.get("data")).get("provider_extra"))
                .isEqualTo("keep-me");
        server.verify();
    }

    private DouyinAuthProperties properties() {
        return new DouyinAuthProperties(
                true,
                "live",
                "client-key",
                "client-secret",
                "https://example.test/callback",
                "user_info",
                "",
                "http://127.0.0.1:8787",
                "",
                180,
                1500,
                5000,
                30000
        );
    }
}
