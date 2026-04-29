package com.github.liyibo1110.feign.codec;

import com.github.liyibo1110.feign.FeignException;
import com.github.liyibo1110.feign.Response;
import com.github.liyibo1110.feign.RetryableException;
import com.github.liyibo1110.feign.Util;

import java.util.Collection;
import java.util.Map;

/**
 * @author liyibo
 * @date 2026-04-28 18:17
 */
public class DefaultErrorDecoder implements ErrorDecoder {
    private final ErrorDecoder.RetryAfterDecoder retryAfterDecoder = new ErrorDecoder.RetryAfterDecoder();
    private Integer maxBodyBytesLength;
    private Integer maxBodyCharsLength;

    public DefaultErrorDecoder() {
        this.maxBodyBytesLength = null;
        this.maxBodyCharsLength = null;
    }

    public DefaultErrorDecoder(Integer maxBodyBytesLength, Integer maxBodyCharsLength) {
        this.maxBodyBytesLength = maxBodyBytesLength;
        this.maxBodyCharsLength = maxBodyCharsLength;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        FeignException exception = FeignException.errorStatus(methodKey, response, maxBodyBytesLength, maxBodyCharsLength);
        Long retryAfter = retryAfterDecoder.apply(firstOrNull(response.headers(), Util.RETRY_AFTER));
        if (retryAfter != null) {
            return new RetryableException(response.status(),
                                          exception.getMessage(),
                                          response.request().httpMethod(),
                                          exception,
                                          retryAfter,
                                          response.request(),
                                          methodKey);
        }
        return exception;
    }

    private <T> T firstOrNull(Map<String, Collection<T>> map, String key) {
        if (map.containsKey(key) && !map.get(key).isEmpty())
            return map.get(key).iterator().next();

        return null;
    }
}
