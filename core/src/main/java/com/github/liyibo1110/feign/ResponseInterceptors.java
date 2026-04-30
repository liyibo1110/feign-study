package com.github.liyibo1110.feign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * response拦截器容器封装
 * @author liyibo
 * @date 2026-04-29 11:11
 */
public final class ResponseInterceptors {

    private final List<ResponseInterceptor> interceptors;

    public ResponseInterceptors(List<ResponseInterceptor> interceptors) {
        this.interceptors = new ArrayList<>(interceptors);
    }

    public List<ResponseInterceptor> interceptors() {
        return Collections.unmodifiableList(interceptors);
    }
}
