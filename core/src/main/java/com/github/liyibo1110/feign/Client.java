package com.github.liyibo1110.feign;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HTTP客户端，负责发送请求并接收响应.
 * @author liyibo
 * @date 2026-04-28 16:48
 */
public interface Client {

    Response execute(Request request, Request.Options options) throws IOException;

    @Deprecated
    class Default extends DefaultClient {
        public Default(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier) {
            super(sslContextFactory, hostnameVerifier);
        }

        public Default(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier, boolean disableRequestBuffering) {
            super(sslContextFactory, hostnameVerifier, disableRequestBuffering);
        }
    }

    class Proxied extends Default {
        public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
        private final Proxy proxy;
        private String credentials;

        public Proxied(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier, Proxy proxy) {
            super(sslContextFactory, hostnameVerifier);
            Util.checkNotNull(proxy, "a proxy is required.");
            this.proxy = proxy;
        }

        public Proxied(SSLSocketFactory sslContextFactory,
                       HostnameVerifier hostnameVerifier,
                       Proxy proxy,
                       String proxyUser,
                       String proxyPassword) {
            this(sslContextFactory, hostnameVerifier, proxy);
            Util.checkArgument(Util.isNotBlank(proxyUser), "proxy user is required.");
            Util.checkArgument(Util.isNotBlank(proxyPassword), "proxy password is required.");
            this.credentials = basic(proxyUser, proxyPassword);
        }

        @Override
        public HttpURLConnection getConnection(URL url) throws IOException {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection(this.proxy);
            if (Util.isNotBlank(this.credentials))
                connection.addRequestProperty(PROXY_AUTHORIZATION, this.credentials);
            return connection;
        }

        public String getCredentials() {
            return this.credentials;
        }

        private String basic(String username, String password) {
            String token = username + ":" + password;
            byte[] bytes = token.getBytes(StandardCharsets.ISO_8859_1);
            String encoded = Base64.getEncoder().encodeToString(bytes);
            return "Basic " + encoded;
        }
    }
}
