package com.github.liyibo1110.feign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * request拦截器容器封装
 * @author liyibo
 * @date 2026-04-28 17:03
 */
public class RequestInterceptors {

    private final List<RequestInterceptor> interceptors;

    public RequestInterceptors(List<RequestInterceptor> interceptors) {
        this.interceptors = new ArrayList<>(interceptors);
    }

    public List<RequestInterceptor> interceptors() {
        return Collections.unmodifiableList(interceptors);
    }
}
