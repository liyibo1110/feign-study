package com.github.liyibo1110.feign.hc5;

import com.github.liyibo1110.feign.AsyncClient;
import com.github.liyibo1110.feign.Request;
import com.github.liyibo1110.feign.Response;
import com.github.liyibo1110.feign.Util;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.io.CloseMode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPOutputStream;

/**
 * AsyncClient接口的实现类，基于hc5。
 * @author liyibo
 * @date 2026-04-30 13:56
 */
public class AsyncApacheHttp5Client implements AsyncClient<HttpClientContext>, AutoCloseable {

    private static final String ACCEPT_HEADER_NAME = "Accept";

    private final CloseableHttpAsyncClient client;

    public AsyncApacheHttp5Client() {
        this(createStartedClient());
    }

    public AsyncApacheHttp5Client(CloseableHttpAsyncClient client) {
        this.client = client;
    }

    private static CloseableHttpAsyncClient createStartedClient() {
        final CloseableHttpAsyncClient client = HttpAsyncClients.custom().build();
        client.start();
        return client;
    }

    @Override
    public CompletableFuture<Response> execute(Request request, Request.Options options, Optional<HttpClientContext> requestContext) {
        final SimpleHttpRequest httpUriRequest = toClassicHttpRequest(request, options);

        final CompletableFuture<Response> result = new CompletableFuture<>();
        final FutureCallback<SimpleHttpResponse> callback =
                new FutureCallback<>() {

                    @Override
                    public void completed(SimpleHttpResponse httpResponse) {
                        result.complete(toFeignResponse(httpResponse, request));
                    }

                    @Override
                    public void failed(Exception ex) {
                        result.completeExceptionally(ex);
                    }

                    @Override
                    public void cancelled() {
                        result.cancel(false);
                    }
                };

        client.execute(httpUriRequest,
                       configureTimeoutsAndRedirection(options, requestContext.orElseGet(HttpClientContext::new)),
                       callback);

        return result;
    }

    protected HttpClientContext configureTimeoutsAndRedirection(Request.Options options, HttpClientContext context) {
        // per request timeouts
        final RequestConfig requestConfig =
                (client instanceof Configurable
                        ? RequestConfig.copy(((Configurable) client).getConfig())
                        : RequestConfig.custom())
                        .setConnectTimeout(options.connectTimeout(), options.connectTimeoutUnit())
                        .setResponseTimeout(options.readTimeout(), options.readTimeoutUnit())
                        .setRedirectsEnabled(options.isFollowRedirects())
                        .build();
        context.setRequestConfig(requestConfig);
        return context;
    }

    SimpleHttpRequest toClassicHttpRequest(Request request, Request.Options options) {
        final SimpleHttpRequest httpRequest = new SimpleHttpRequest(request.httpMethod().name(), request.url());

        // request headers
        boolean hasAcceptHeader = false;
        boolean isGzip = false;
        for (final Map.Entry<String, Collection<String>> headerEntry : request.headers().entrySet()) {
            final String headerName = headerEntry.getKey();
            if (headerName.equalsIgnoreCase(ACCEPT_HEADER_NAME))
                hasAcceptHeader = true;

            if (headerName.equalsIgnoreCase(Util.CONTENT_LENGTH)) {
                // The 'Content-Length' header is always set by the Apache client and it
                // doesn't like us to set it as well.
                continue;
            }
            if (headerName.equalsIgnoreCase(Util.CONTENT_ENCODING)) {
                isGzip = headerEntry.getValue().stream().anyMatch(Util.ENCODING_GZIP::equalsIgnoreCase);
                boolean isDeflate = headerEntry.getValue().stream().anyMatch(Util.ENCODING_DEFLATE::equalsIgnoreCase);
                if (isDeflate) {
                    // DeflateCompressingEntity not available in hc5 yet
                    throw new IllegalArgumentException("Deflate Content-Encoding is not supported by feign-hc5");
                }
            }

            for (final String headerValue : headerEntry.getValue())
                httpRequest.addHeader(headerName, headerValue);
        }
        // some servers choke on the default accept string, so we'll set it to anything
        if (!hasAcceptHeader)
            httpRequest.addHeader(ACCEPT_HEADER_NAME, "*/*");

        // request body
        // final Body requestBody = request.requestBody();
        byte[] data = request.body();
        if (isGzip && data != null && data.length > 0) {
            // compress if needed
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 GZIPOutputStream gzipOs = new GZIPOutputStream(baos, true)) {
                gzipOs.write(data);
                gzipOs.flush();
                data = baos.toByteArray();
            } catch (IOException suppressed) { // NOPMD

            }
        }
        if (data != null)
            httpRequest.setBody(data, getContentType(request));

        return httpRequest;
    }

    private ContentType getContentType(Request request) {
        ContentType contentType = null;
        for (final Map.Entry<String, Collection<String>> entry : request.headers().entrySet()) {
            if (entry.getKey().equalsIgnoreCase("Content-Type")) {
                final Collection<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    contentType = ContentType.parse(values.iterator().next());
                    if (contentType.getCharset() == null)
                        contentType = contentType.withCharset(request.charset());
                    break;
                }
            }
        }
        return contentType;
    }

    Response toFeignResponse(SimpleHttpResponse httpResponse, Request request) {
        final int statusCode = httpResponse.getCode();

        final String reason = httpResponse.getReasonPhrase();

        final Map<String, Collection<String>> headers = new HashMap<>();
        for (final Header header : httpResponse.getHeaders()) {
            final String name = header.getName();
            final String value = header.getValue();

            Collection<String> headerValues = headers.get(name);
            if (headerValues == null) {
                headerValues = new ArrayList<>();
                headers.put(name, headerValues);
            }
            headerValues.add(value);
        }

        return Response.builder()
                .protocolVersion(Util.enumForName(Request.ProtocolVersion.class, httpResponse.getVersion().format()))
                .status(statusCode)
                .reason(reason)
                .headers(headers)
                .request(request)
                .body(httpResponse.getBodyBytes())
                .build();
    }

    @Override
    public void close() throws Exception {
        client.close(CloseMode.GRACEFUL);
    }
}
