package com.github.liyibo1110.feign;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

/**
 * 具有不可变性。
 * @author liyibo
 * @date 2026-04-27 17:39
 */
public final class Response implements Closeable {

    private final int status;
    private final String reason;
    private final Map<String, Collection<String>> headers;
    private final Body body;
    private final Request request;
    private final Request.ProtocolVersion protocolVersion;

    private Response(Builder builder) {
        Util.checkState(builder.request != null, "original request is required");
        this.status = builder.status;
        this.request = builder.request;
        this.reason = builder.reason; // nullable
        this.headers = Util.caseInsensitiveCopyOf(builder.headers);
        this.body = builder.body; // nullable
        this.protocolVersion = builder.protocolVersion;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private static final Request.ProtocolVersion DEFAULT_PROTOCOL_VERSION = Request.ProtocolVersion.HTTP_1_1;

        int status;
        String reason;
        Map<String, Collection<String>> headers;
        Body body;
        Request request;
        private RequestTemplate requestTemplate;
        private Request.ProtocolVersion protocolVersion = DEFAULT_PROTOCOL_VERSION;

        Builder() {}

        Builder(Response source) {
            this.status = source.status;
            this.reason = source.reason;
            this.headers = source.headers;
            this.body = source.body;
            this.request = source.request;
            this.protocolVersion = source.protocolVersion;
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

        public Builder body(Body body) {
            this.body = body;
            return this;
        }

        public Builder body(InputStream inputStream, Integer length) {
            this.body = InputStreamBody.orNull(inputStream, length);
            return this;
        }

        public Builder body(byte[] data) {
            this.body = ByteArrayBody.orNull(data);
            return this;
        }

        public Builder body(String text, Charset charset) {
            this.body = ByteArrayBody.orNull(text, charset);
            return this;
        }

        public Builder request(Request request) {
            Util.checkNotNull(request, "request is required");
            this.request = request;
            return this;
        }

        public Builder protocolVersion(Request.ProtocolVersion protocolVersion) {
            this.protocolVersion = (protocolVersion != null) ? protocolVersion : DEFAULT_PROTOCOL_VERSION;
            return this;
        }

        @Experimental
        public Builder requestTemplate(RequestTemplate requestTemplate) {
            this.requestTemplate = requestTemplate;
            return this;
        }

        public Response build() {
            return new Response(this);
        }
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

    public Body body() {
        return body;
    }

    public Request request() {
        return request;
    }

    public Request.ProtocolVersion protocolVersion() {
        return protocolVersion;
    }

    /**
     * header string -> Charset
     */
    public Charset charset() {
        Collection<String> contentTypeHeaders = headers().get("Content-Type");

        if (contentTypeHeaders != null) {
            for (String contentTypeHeader : contentTypeHeaders) {
                String[] contentTypeParmeters = contentTypeHeader.split(";");
                if (contentTypeParmeters.length > 1) {
                    String[] charsetParts = contentTypeParmeters[1].split("=");
                    if (charsetParts.length == 2 && "charset".equalsIgnoreCase(charsetParts[0].trim())) {
                        String charsetString = charsetParts[1].replaceAll("\"", "");
                        return Charset.forName(charsetString);
                    }
                }
            }
        }

        return Util.UTF_8;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(protocolVersion.toString()).append(" ").append(status);
        if (reason != null)
            builder.append(' ').append(reason);
        builder.append('\n');
        for (String field : headers.keySet()) {
            for (String value : Util.valuesOrEmpty(headers, field))
                builder.append(field).append(": ").append(value).append('\n');
        }
        if (body != null)
            builder.append('\n').append(body);
        return builder.toString();
    }

    @Override
    public void close() {
        Util.ensureClosed(body);
    }

    public interface Body extends Closeable {
        Integer length();

        boolean isRepeatable();

        InputStream asInputStream() throws IOException;

        @Deprecated
        default Reader asReader() throws IOException {
            return asReader(StandardCharsets.UTF_8);
        }

        Reader asReader(Charset charset) throws IOException;
    }

    private static final class InputStreamBody implements Response.Body {
        private final InputStream inputStream;
        private final Integer length;

        private InputStreamBody(InputStream inputStream, Integer length) {
            this.inputStream = inputStream;
            this.length = length;
        }

        private static Body orNull(InputStream inputStream, Integer length) {
            if (inputStream == null)
                return null;
            return new InputStreamBody(inputStream, length);
        }

        @Override
        public Integer length() {
            return length;
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public InputStream asInputStream() {
            return inputStream;
        }

        @Override
        public Reader asReader() {
            return new InputStreamReader(inputStream, Util.UTF_8);
        }

        @Override
        public Reader asReader(Charset charset) {
            Util.checkNotNull(charset, "charset should not be null");
            return new InputStreamReader(inputStream, charset);
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }

    private static final class ByteArrayBody implements Response.Body {
        private final byte[] data;

        public ByteArrayBody(byte[] data) {
            this.data = data;
        }

        private static Body orNull(byte[] data) {
            if (data == null)
                return null;
            return new ByteArrayBody(data);
        }

        private static Body orNull(String text, Charset charset) {
            if (text == null)
                return null;
            Util.checkNotNull(charset, "charset");
            return new ByteArrayBody(text.getBytes(charset));
        }

        @Override
        public Integer length() {
            return data.length;
        }

        @Override
        public boolean isRepeatable() {
            return true;
        }

        @Override
        public InputStream asInputStream() {
            return new ByteArrayInputStream(data);
        }

        @SuppressWarnings("deprecation")
        @Override
        public Reader asReader() {
            return new InputStreamReader(asInputStream(), Util.UTF_8);
        }

        @Override
        public Reader asReader(Charset charset) {
            Util.checkNotNull(charset, "charset should not be null");
            return new InputStreamReader(asInputStream(), charset);
        }

        @Override
        public void close() {
            // nothing to do
        }
    }
}
