package com.github.liyibo1110.feign;

import java.util.Collection;
import java.util.Map;

/**
 * @author liyibo
 * @date 2026-04-28 17:49
 */
public final class TypedResponse<T> {

    private final int status;
    private final String reason;
    private final Map<String, Collection<String>> headers;
    private final T body;
    private final Request request;
    private final Request.ProtocolVersion protocolVersion;

    private TypedResponse(Builder<T> builder) {
        Util.checkState(builder.request != null, "original request is required");
        this.status = builder.status;
        this.request = builder.request;
        this.reason = builder.reason; // nullable
        this.headers = Util.caseInsensitiveCopyOf(builder.headers);
        this.body = builder.body;
        this.protocolVersion = builder.protocolVersion;
    }

    public int status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    public Map<String, Collection<String>> headers() {
        return headers;
    }

    public T body() {
        return body;
    }

    public Request request() {
        return request;
    }

    public Request.ProtocolVersion protocolVersion() {
        return protocolVersion;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(protocolVersion.toString()).append(" ").append(status);
        if (reason != null) builder.append(' ').append(reason);
        builder.append('\n');
        for (String field : headers.keySet()) {
            for (String value : Util.valuesOrEmpty(headers, field)) {
                builder.append(field).append(": ").append(value).append('\n');
            }
        }
        if (body != null)
            builder.append('\n').append(body);
        return builder.toString();
    }

    public static <T> Builder<T> builder() {
        return new Builder<T>();
    }

    public static <T> Builder<T> builder(Response source) {
        return new Builder<T>(source);
    }

    public static final class Builder<T> {
        int status;
        String reason;
        Map<String, Collection<String>> headers;
        T body;
        Request request;
        private Request.ProtocolVersion protocolVersion = Request.ProtocolVersion.HTTP_1_1;

        Builder() {}

        Builder(Response source) {
            this.status = source.status();
            this.reason = source.reason();
            this.headers = source.headers();
            this.request = source.request();
            this.protocolVersion = source.protocolVersion();
        }

        public Builder status(int status) {
            this.status = status;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder headers(Map<String, Collection<String>> headers) {
            this.headers = headers;
            return this;
        }

        public Builder body(T body) {
            this.body = body;
            return this;
        }

        public Builder request(Request request) {
            Util.checkNotNull(request, "request is required");
            this.request = request;
            return this;
        }

        public Builder protocolVersion(Request.ProtocolVersion protocolVersion) {
            this.protocolVersion = protocolVersion;
            return this;
        }

        public TypedResponse build() {
            return new TypedResponse<T>(this);
        }
    }
}
