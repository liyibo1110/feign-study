package com.github.liyibo1110.feign;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import static java.util.Objects.nonNull;

/**
 * 用于调试消息的简单日志抽象。
 * 改编自retrofit.RestAdapter.Log。
 * @author liyibo
 * @date 2026-04-24 10:21
 */
public abstract class Logger {

    protected static String methodTag(String configKey) {
        return '[' + configKey.substring(0, configKey.indexOf('(')) + "] ";
    }

    /**
     * 重写该方法，以使用你自己的实现来记录请求和响应。
     * 消息内容将是HTTP请求和响应的文本。
     * @param configKey value of {@link Feign#configKey(Class, java.lang.reflect.Method)}
     * @param format {@link java.util.Formatter format string}
     * @param args arguments applied to {@code format}
     */
    protected abstract void log(String configKey, String format, Object... args);

    /**
     * 是否记录request header。
     * @param header header name
     */
    protected boolean shouldLogRequestHeader(String header) {
        return true;
    }

    /**
     * 是否记录response header。
     * @param header header name
     */
    protected boolean shouldLogResponseHeader(String header) {
        return true;
    }

    protected void logRequest(String configKey, Level logLevel, Request request) {
        String protocolVersion = resolveProtocolVersion(request.protocolVersion());
        // 输出request first line
        log(configKey, "---> %s %s %s", request.httpMethod().name(), request.url(), protocolVersion);
        if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {
            // 输出request header
            for (String field : request.headers().keySet()) {
                if (shouldLogRequestHeader(field)) {
                    for (String value : Util.valuesOrEmpty(request.headers(), field)) {
                        log(configKey, "%s: %s", field, value);
                    }
                }
            }

            // 输出request body
            int bodyLength = 0;
            if (request.body() != null) {
                bodyLength = request.length();
                if (logLevel.ordinal() >= Level.FULL.ordinal()) {
                    String bodyText = request.charset() != null ? new String(request.body(), request.charset()) : null;
                    log(configKey, ""); // CRLF
                    log(configKey, "%s", bodyText != null ? bodyText : "Binary data");
                }
            }
            log(configKey, "---> END HTTP (%s-byte body)", bodyLength);
        }
    }

    protected void logRetry(String configKey, Level logLevel) {
        log(configKey, "---> RETRYING");
    }

    protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
        String protocolVersion = resolveProtocolVersion(response.protocolVersion());
        String reason = response.reason() != null && logLevel.compareTo(Level.NONE) > 0
                        ? " " + response.reason()
                        : "";
        int status = response.status();
        // 输出response first line
        log(configKey, "<--- %s %s%s (%sms)", protocolVersion, status, reason, elapsedTime);
        if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {
            // 输出response header
            for (String field : response.headers().keySet()) {
                if (shouldLogResponseHeader(field)) {
                    for (String value : Util.valuesOrEmpty(response.headers(), field)) {
                        log(configKey, "%s: %s", field, value);
                    }
                }
            }

            // 输出response body
            int bodyLength = 0;
            if (response.body() != null && !(status == 204 || status == 205)) {
                if (logLevel.ordinal() >= Level.FULL.ordinal())
                    log(configKey, ""); // CRLF

                byte[] bodyData = Util.toByteArray(response.body().asInputStream());
                ensureClosed(response.body());
                bodyLength = bodyData.length;
                if (logLevel.ordinal() >= Level.FULL.ordinal() && bodyLength > 0)
                    log(configKey, "%s", decodeOrDefault(bodyData, UTF_8, "Binary data"));
                log(configKey, "<--- END HTTP (%s-byte body)", bodyLength);
                return response.toBuilder().body(bodyData).build();
            } else {
                log(configKey, "<--- END HTTP (%s-byte body)", bodyLength);
            }
        }
        return response;
    }

    protected IOException logIOException(String configKey, Level logLevel, IOException ioe, long elapsedTime) {
        log(configKey, "<--- ERROR %s: %s (%sms)", ioe.getClass().getSimpleName(), ioe.getMessage(), elapsedTime);
        if (logLevel.ordinal() >= Level.FULL.ordinal()) {
            StringWriter sw = new StringWriter();
            ioe.printStackTrace(new PrintWriter(sw));
            log(configKey, "%s", sw.toString());
            log(configKey, "<--- END ERROR");
        }
        return ioe;
    }

    protected static String resolveProtocolVersion(Request.ProtocolVersion protocolVersion) {
        if (nonNull(protocolVersion))
            return protocolVersion.toString();
        return "UNKNOWN";
    }

    /**
     * 日志等级
     */
    public enum Level {
        /** 不记录 */
        NONE,
        /** 仅记录请求方法、URL、响应状态码和执行时间 */
        BASIC,
        /** 记录基本信息以及请求和响应头 */
        HEADERS,
        /** 记录请求和响应的头部、正文及元数据 */
        FULL
    }

    /**
     * 输出到System.err。
     */
    public static class ErrorLogger extends Logger {
        @Override
        protected void log(String configKey, String format, Object... args) {
            System.err.printf(methodTag(configKey) + format + "%n", args);
        }
    }

    /**
     * 输出到java.util.Logging。
     */
    public static class JavaLogger extends Logger {
        final java.util.logging.Logger logger;

        @Deprecated
        public JavaLogger() {
            logger = java.util.logging.Logger.getLogger(Logger.class.getName());
        }

        public JavaLogger(String loggerName) {
            logger = java.util.logging.Logger.getLogger(loggerName);
        }

        public JavaLogger(Class<?> clazz) {
            logger = java.util.logging.Logger.getLogger(clazz.getName());
        }

        @Override
        protected void logRequest(String configKey, Level logLevel, Request request) {
            if (logger.isLoggable(java.util.logging.Level.FINE))
                super.logRequest(configKey, logLevel, request);
        }

        @Override
        protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime)
                throws IOException {
            if (logger.isLoggable(java.util.logging.Level.FINE))
                return super.logAndRebufferResponse(configKey, logLevel, response, elapsedTime);
            return response;
        }

        @Override
        protected void log(String configKey, String format, Object... args) {
            if (logger.isLoggable(java.util.logging.Level.FINE))
                logger.fine(String.format(methodTag(configKey) + format, args));
        }

        public JavaLogger appendToFile(String logfile) {
            logger.setLevel(java.util.logging.Level.FINE);
            try {
                FileHandler handler = new FileHandler(logfile, true);
                handler.setFormatter(
                        new SimpleFormatter() {
                            @Override
                            public String format(LogRecord record) {
                                return String.format("%s%n", record.getMessage()); // NOPMD
                            }
                        });
                logger.addHandler(handler);
            } catch (IOException e) {
                throw new IllegalStateException("Could not add file handler.", e);
            }
            return this;
        }
    }

    /**
     * 不输出任何log的实现。
     */
    public static class NoOpLogger extends Logger {
        @Override
        protected void logRequest(String configKey, Level logLevel, Request request) {}

        @Override
        protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
            return response;
        }

        @Override
        protected void log(String configKey, String format, Object... args) {}
    }
}
