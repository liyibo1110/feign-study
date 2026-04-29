package com.github.liyibo1110.feign;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * 当响应被视为可重试时，会抛出此异常，通常是在状态码为503时通过feign.codec.ErrorDecoder触发。
 * @author liyibo
 * @date 2026-04-28 17:45
 */
public class RetryableException extends FeignException {
    private static final long serialVersionUID = 3L;

    private final Long retryAfter;
    private final Request.HttpMethod httpMethod;
    private final String methodKey;

    public RetryableException(int status, String message, Request.HttpMethod httpMethod, Request request) {
        super(status, message, request);
        this.httpMethod = httpMethod;
        this.retryAfter = null;
        this.methodKey = null;
    }

    public RetryableException(int status, String message, Request.HttpMethod httpMethod, Throwable cause, Request request) {
        super(status, message, request, cause);
        this.httpMethod = httpMethod;
        this.retryAfter = null;
        this.methodKey = null;
    }

    public RetryableException(int status,
                              String message,
                              Request.HttpMethod httpMethod,
                              Throwable cause,
                              Long retryAfter,
                              Request request) {
        super(status, message, request, cause);
        this.httpMethod = httpMethod;
        this.retryAfter = retryAfter;
        this.methodKey = null;
    }

    public RetryableException(int status,
                              String message,
                              Request.HttpMethod httpMethod,
                              Throwable cause,
                              Long retryAfter,
                              Request request,
                              String methodKey) {
        super(status, message, request, cause);
        this.httpMethod = httpMethod;
        this.retryAfter = retryAfter;
        this.methodKey = methodKey;
    }

    @Deprecated
    public RetryableException(int status,
                              String message,
                              Request.HttpMethod httpMethod,
                              Throwable cause,
                              Date retryAfter,
                              Request request) {
        super(status, message, request, cause);
        this.httpMethod = httpMethod;
        this.retryAfter = retryAfter != null ? retryAfter.getTime() : null;
        this.methodKey = null;
    }

    public RetryableException(int status, String message, Request.HttpMethod httpMethod, Long retryAfter, Request request) {
        super(status, message, request);
        this.httpMethod = httpMethod;
        this.retryAfter = retryAfter;
        this.methodKey = null;
    }

    @Deprecated
    public RetryableException(int status, String message, Request.HttpMethod httpMethod, Date retryAfter, Request request) {
        super(status, message, request);
        this.httpMethod = httpMethod;
        this.retryAfter = retryAfter != null ? retryAfter.getTime() : null;
        this.methodKey = null;
    }

    public RetryableException(int status,
                              String message,
                              Request.HttpMethod httpMethod,
                              Long retryAfter,
                              Request request,
                              byte[] responseBody,
                              Map<String, Collection<String>> responseHeaders) {
        super(status, message, request, responseBody, responseHeaders);
        this.httpMethod = httpMethod;
        this.retryAfter = retryAfter;
        this.methodKey = null;
    }

    @Deprecated
    public RetryableException(int status,
                              String message,
                              Request.HttpMethod httpMethod,
                              Date retryAfter,
                              Request request,
                              byte[] responseBody,
                              Map<String, Collection<String>> responseHeaders) {
        super(status, message, request, responseBody, responseHeaders);
        this.httpMethod = httpMethod;
        this.retryAfter = retryAfter != null ? retryAfter.getTime() : null;
        this.methodKey = null;
    }

    public Long retryAfter() {
        return retryAfter;
    }

    public Request.HttpMethod method() {
        return this.httpMethod;
    }

    public String methodKey() {
        return this.methodKey;
    }
}
