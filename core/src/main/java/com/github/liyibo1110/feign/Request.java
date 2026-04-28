package com.github.liyibo1110.feign;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 具有不可变性，最终用来传递给Client组件的execute方法来发起真正的HTTP请求。
 * @author liyibo
 * @date 2026-04-27 17:30
 */
public final class Request implements Serializable {

    public enum HttpMethod {
        GET,
        HEAD,
        POST(true),
        PUT(true),
        DELETE,
        CONNECT,
        OPTIONS,
        TRACE,
        PATCH(true);

        private final boolean withBody;

        HttpMethod() {
            this(false);
        }

        HttpMethod(boolean withBody) {
            this.withBody = withBody;
        }

        public boolean isWithBody() {
            return this.withBody;
        }
    }

    public enum ProtocolVersion {
        HTTP_1_0("HTTP/1.0"),
        HTTP_1_1("HTTP/1.1"),
        HTTP_2("HTTP/2.0"),
        MOCK;

        final String protocolVersion;

        ProtocolVersion() {
            protocolVersion = name();
        }

        ProtocolVersion(String protocolVersion) {
            this.protocolVersion = protocolVersion;
        }

        @Override
        public String toString() {
            return protocolVersion;
        }
    }

    @Deprecated
    public static Request create(String method,
                                 String url,
                                 Map<String, Collection<String>> headers,
                                 byte[] body,
                                 Charset charset) {
        Util.checkNotNull(method, "httpMethod of %s", method);
        final HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
        return create(httpMethod, url, headers, body, charset, null);
    }

    @Deprecated
    public static Request create(HttpMethod httpMethod,
                                 String url,
                                 Map<String, Collection<String>> headers,
                                 byte[] body,
                                 Charset charset) {
        return create(httpMethod, url, headers, Request.Body.create(body, charset), null);
    }

    public static Request create(HttpMethod httpMethod,
                                 String url,
                                 Map<String, Collection<String>> headers,
                                 byte[] body,
                                 Charset charset,
                                 RequestTemplate requestTemplate) {
        return create(httpMethod, url, headers, Request.Body.create(body, charset), requestTemplate);
    }

    public static Request create(HttpMethod httpMethod,
                                 String url,
                                 Map<String, Collection<String>> headers,
                                 Request.Body body,
                                 RequestTemplate requestTemplate) {
        return new Request(httpMethod, url, headers, body, requestTemplate);
    }

    private final HttpMethod httpMethod;
    private final String url;
    private final Map<String, Collection<String>> headers;
    private final Request.Body body;
    private final RequestTemplate requestTemplate;
    private final ProtocolVersion protocolVersion;

    Request(HttpMethod method,
            String url,
            Map<String, Collection<String>> headers,
            Request.Body body,
            RequestTemplate requestTemplate) {
        this.httpMethod = Util.checkNotNull(method, "httpMethod of %s", method.name());
        this.url = Util.checkNotNull(url, "url");
        this.headers = Util.checkNotNull(headers, "headers of %s %s", method, url);
        this.body = body;
        this.requestTemplate = requestTemplate;
        protocolVersion = ProtocolVersion.HTTP_1_1;
    }

    @Deprecated
    public String method() {
        return httpMethod.name();
    }

    public HttpMethod httpMethod() {
        return this.httpMethod;
    }

    public String url() {
        return url;
    }

    public Map<String, Collection<String>> headers() {
        return Collections.unmodifiableMap(headers);
    }

    public void header(String key, String value) {
        header(key, Arrays.asList(value));
    }

    public void header(String key, Collection<String> values) {
        headers.put(key, values);
    }

    public Charset charset() {
        return body.encoding;
    }

    public byte[] body() {
        return body.data;
    }

    public boolean isBinary() {
        return body.isBinary();
    }

    public int length() {
        return this.body.length();
    }

    public ProtocolVersion protocolVersion() {
        return protocolVersion;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(httpMethod)
               .append(' ')
               .append(url)
               .append(' ')
               .append(protocolVersion)
               .append('\n');
        for (final String field : headers.keySet()) {
            for (final String value : Util.valuesOrEmpty(headers, field))
                builder.append(field).append(": ").append(value).append('\n');
        }
        if (body != null)
            builder.append('\n').append(body.asString());

        return builder.toString();
    }

    public static class Options {
        private final long connectTimeout;
        private final TimeUnit connectTimeoutUnit;
        private final long readTimeout;
        private final TimeUnit readTimeoutUnit;
        private final boolean followRedirects;
        private final Map<String, Map<String, Options>> threadToMethodOptions;

        @Experimental
        public Options getMethodOptions(String methodName) {
            Map<String, Options> methodOptions = threadToMethodOptions.getOrDefault(Util.getThreadIdentifier(), new HashMap<>());
            return methodOptions.getOrDefault(methodName, this);
        }

        @Experimental
        public void setMethodOptions(String methodName, Options options) {
            String threadIdentifier = Util.getThreadIdentifier();
            Map<String, Request.Options> methodOptions = threadToMethodOptions.getOrDefault(threadIdentifier, new HashMap<>());
            threadToMethodOptions.put(threadIdentifier, methodOptions);
            methodOptions.put(methodName, options);
        }

        @Deprecated
        public Options(int connectTimeoutMillis, int readTimeoutMillis, boolean followRedirects) {
            this(connectTimeoutMillis, TimeUnit.MILLISECONDS, readTimeoutMillis, TimeUnit.MILLISECONDS, followRedirects);
        }

        public Options(long connectTimeout, TimeUnit connectTimeoutUnit, long readTimeout, TimeUnit readTimeoutUnit, boolean followRedirects) {
            super();
            this.connectTimeout = connectTimeout;
            this.connectTimeoutUnit = connectTimeoutUnit;
            this.readTimeout = readTimeout;
            this.readTimeoutUnit = readTimeoutUnit;
            this.followRedirects = followRedirects;
            this.threadToMethodOptions = new ConcurrentHashMap<>();
        }

        @Deprecated
        public Options(int connectTimeoutMillis, int readTimeoutMillis) {
            this(connectTimeoutMillis, readTimeoutMillis, true);
        }

        public Options(Duration connectTimeout, Duration readTimeout, boolean followRedirects) {
            this(connectTimeout.toMillis(), TimeUnit.MILLISECONDS, readTimeout.toMillis(), TimeUnit.MILLISECONDS, followRedirects);
        }

        public Options() {
            this(10, TimeUnit.SECONDS, 60, TimeUnit.SECONDS, true);
        }

        public int connectTimeoutMillis() {
            return (int) connectTimeoutUnit.toMillis(connectTimeout);
        }

        public int readTimeoutMillis() {
            return (int) readTimeoutUnit.toMillis(readTimeout);
        }

        public boolean isFollowRedirects() {
            return followRedirects;
        }

        public long connectTimeout() {
            return connectTimeout;
        }

        public TimeUnit connectTimeoutUnit() {
            return connectTimeoutUnit;
        }

        public long readTimeout() {
            return readTimeout;
        }

        public TimeUnit readTimeoutUnit() {
            return readTimeoutUnit;
        }
    }

    @Experimental
    public RequestTemplate requestTemplate() {
        return this.requestTemplate;
    }

    @Experimental
    public static class Body implements Serializable {
        private transient Charset encoding;

        private byte[] data;

        private Body() {
            super();
        }

        private Body(byte[] data) {
            this.data = data;
        }

        private Body(byte[] data, Charset encoding) {
            this.data = data;
            this.encoding = encoding;
        }

        public Optional<Charset> getEncoding() {
            return Optional.ofNullable(this.encoding);
        }

        public int length() {
            /* calculate the content length based on the data provided */
            return data != null ? data.length : 0;
        }

        public byte[] asBytes() {
            return data;
        }

        public String asString() {
            return !isBinary() ? new String(data, encoding) : "Binary data";
        }

        public boolean isBinary() {
            return encoding == null || data == null;
        }

        public static Body create(String data) {
            return new Body(data.getBytes());
        }

        public static Body create(String data, Charset charset) {
            return new Body(data.getBytes(charset), charset);
        }

        public static Body create(byte[] data) {
            return new Body(data);
        }

        public static Body create(byte[] data, Charset charset) {
            return new Body(data, charset);
        }

        @Deprecated
        public static Body encoded(byte[] data, Charset charset) {
            return create(data, charset);
        }

        public static Body empty() {
            return new Body();
        }
    }
}
