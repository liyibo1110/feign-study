package com.github.liyibo1110.feign;

/**
 * request拦截处理器
 * @author liyibo
 * @date 2026-04-28 17:03
 */
public interface RequestInterceptor {

    void apply(RequestTemplate template);
}
