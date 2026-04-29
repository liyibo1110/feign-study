package com.github.liyibo1110.feign;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.lang.String.format;

/**
 * @author liyibo
 * @date 2026-04-28 16:49
 */
public class DefaultClient implements Client {
    private final SSLSocketFactory sslContextFactory;
    private final HostnameVerifier hostnameVerifier;

    /** 是否禁用HttpURLConnection的请求正文内部缓冲 */
    private final boolean disableRequestBuffering;

    public DefaultClient(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier) {
        this.sslContextFactory = sslContextFactory;
        this.hostnameVerifier = hostnameVerifier;
        this.disableRequestBuffering = true;
    }

    public DefaultClient(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier, boolean disableRequestBuffering) {
        super();
        this.sslContextFactory = sslContextFactory;
        this.hostnameVerifier = hostnameVerifier;
        this.disableRequestBuffering = disableRequestBuffering;
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        HttpURLConnection connection = convertAndSend(request, options);
        return convertResponse(connection, request);
    }

    Response convertResponse(HttpURLConnection connection, Request request) throws IOException {
        int status = connection.getResponseCode();
        String reason = connection.getResponseMessage();

        if (status < 0)
            throw new IOException(format("Invalid status(%s) executing %s %s", status, connection.getRequestMethod(), connection.getURL()));

        Map<String, Collection<String>> headers = new TreeMap<>(CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, List<String>> field : connection.getHeaderFields().entrySet()) {
            // response message
            if (field.getKey() != null)
                headers.put(field.getKey(), field.getValue());
        }

        Integer length = connection.getContentLength();
        if (length == -1)
            length = null;

        InputStream stream;
        if (status >= 400)
            stream = connection.getErrorStream();
        else
            stream = connection.getInputStream();

        if (stream != null && this.isGzip(headers.get(Util.CONTENT_ENCODING)))
            stream = new GZIPInputStream(stream);
        else if (stream != null && this.isDeflate(headers.get(Util.CONTENT_ENCODING)))
            stream = new InflaterInputStream(stream);

        return Response.builder()
                .status(status)
                .reason(reason)
                .headers(headers)
                .request(request)
                .body(stream, length)
                .build();
    }

    public HttpURLConnection getConnection(final URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    HttpURLConnection convertAndSend(Request request, Request.Options options) throws IOException {
        final URL url = new URL(request.url());
        final HttpURLConnection connection = this.getConnection(url);
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection sslCon = (HttpsURLConnection) connection;
            if (sslContextFactory != null)
                sslCon.setSSLSocketFactory(sslContextFactory);
            if (hostnameVerifier != null)
                sslCon.setHostnameVerifier(hostnameVerifier);
        }
        connection.setConnectTimeout(options.connectTimeoutMillis());
        connection.setReadTimeout(options.readTimeoutMillis());
        connection.setAllowUserInteraction(false);
        connection.setInstanceFollowRedirects(options.isFollowRedirects());
        connection.setRequestMethod(request.httpMethod().name());

        Collection<String> contentEncodingValues = request.headers().get(Util.CONTENT_ENCODING);
        boolean gzipEncodedRequest = this.isGzip(contentEncodingValues);
        boolean deflateEncodedRequest = this.isDeflate(contentEncodingValues);

        boolean hasAcceptHeader = false;
        Integer contentLength = null;
        for (String field : request.headers().keySet()) {
            if (field.equalsIgnoreCase("Accept"))
                hasAcceptHeader = true;

            for (String value : request.headers().get(field)) {
                if (field.equals(Util.CONTENT_LENGTH)) {
                    if (!gzipEncodedRequest && !deflateEncodedRequest) {
                        contentLength = Integer.valueOf(value);
                        connection.addRequestProperty(field, value);
                    }
                }
                // Avoid add "Accept-encoding" twice or more when "compression" option is enabled
                else if (field.equals(Util.ACCEPT_ENCODING)) {
                    connection.addRequestProperty(field, String.join(", ", request.headers().get(field)));
                    break;
                } else {
                    connection.addRequestProperty(field, value);
                }
            }
        }
        // Some servers choke on the default accept string.
        if (!hasAcceptHeader)
            connection.addRequestProperty("Accept", "*/*");

        byte[] body = request.body();

        if (body != null) {
            /*
             * Ignore disableRequestBuffering flag if the empty body was set, to ensure that internal
             * retry logic applies to such requests.
             */
            if (disableRequestBuffering) {
                if (contentLength != null)
                    connection.setFixedLengthStreamingMode(contentLength);
                else
                    connection.setChunkedStreamingMode(8196);
            }
            connection.setDoOutput(true);
            OutputStream out = connection.getOutputStream();
            if (gzipEncodedRequest)
                out = new GZIPOutputStream(out);
            else if (deflateEncodedRequest)
                out = new DeflaterOutputStream(out);

            try {
                out.write(body);
            } finally {
                try {
                    out.close();
                } catch (IOException suppressed) { // NOPMD

                }
            }
        }

        if (body == null && request.httpMethod().isWithBody()) {
            // To use this Header, set 'sun.net.http.allowRestrictedHeaders' property true.
            connection.addRequestProperty("Content-Length", "0");
        }

        return connection;
    }

    private boolean isGzip(Collection<String> contentEncodingValues) {
        return contentEncodingValues != null
                && !contentEncodingValues.isEmpty()
                && contentEncodingValues.contains(Util.ENCODING_GZIP);
    }

    private boolean isDeflate(Collection<String> contentEncodingValues) {
        return contentEncodingValues != null
                && !contentEncodingValues.isEmpty()
                && contentEncodingValues.contains(Util.ENCODING_DEFLATE);
    }
}
