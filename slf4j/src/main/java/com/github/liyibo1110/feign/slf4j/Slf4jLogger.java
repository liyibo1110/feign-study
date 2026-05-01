package com.github.liyibo1110.feign.slf4j;

import com.github.liyibo1110.feign.Logger;
import com.github.liyibo1110.feign.Request;
import com.github.liyibo1110.feign.Response;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author liyibo
 * @date 2026-04-30 14:33
 */
public class Slf4jLogger extends Logger {

    private final org.slf4j.Logger logger;

    public Slf4jLogger() {
        this(Logger.class);
    }

    public Slf4jLogger(Class<?> clazz) {
        this(LoggerFactory.getLogger(clazz));
    }

    public Slf4jLogger(String name) {
        this(LoggerFactory.getLogger(name));
    }

    public Slf4jLogger(org.slf4j.Logger logger) {
        this.logger = logger;
    }

    @Override
    protected void logRequest(String configKey, Level logLevel, Request request) {
        if (logger.isDebugEnabled())
            super.logRequest(configKey, logLevel, request);
    }

    @Override
    protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
        if (logger.isDebugEnabled())
            return super.logAndRebufferResponse(configKey, logLevel, response, elapsedTime);
        return response;
    }

    @Override
    protected void log(String configKey, String format, Object... args) {
        if (logger.isDebugEnabled())
            logger.debug(String.format(methodTag(configKey) + format, args));
    }
}
