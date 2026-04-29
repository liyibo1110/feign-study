package com.github.liyibo1110.feign.auth;

import com.github.liyibo1110.feign.RequestInterceptor;
import com.github.liyibo1110.feign.RequestTemplate;
import com.github.liyibo1110.feign.Util;

import java.nio.charset.Charset;

/**
 * 用于添加使用HTTP基本身份验证所需请求头部的拦截器。
 * @author liyibo
 * @date 2026-04-28 17:04
 */
public class BasicAuthRequestInterceptor implements RequestInterceptor {

    private final String headerValue;

    public BasicAuthRequestInterceptor(String username, String password) {
        this(username, password, Util.ISO_8859_1);
    }

    public BasicAuthRequestInterceptor(String username, String password, Charset charset) {
        Util.checkNotNull(username, "username");
        Util.checkNotNull(password, "password");
        this.headerValue = "Basic " + base64Encode((username + ":" + password).getBytes(charset));
    }

    private static String base64Encode(byte[] bytes) {
        return Base64.encode(bytes);
    }

    @Override
    public void apply(RequestTemplate template) {
        template.header("Authorization", headerValue);
    }
}
