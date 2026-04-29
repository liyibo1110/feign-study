package com.github.liyibo1110.feign;

/**
 * javax.ws.rs.client.WebTarget类似，因为它会生成请求。
 * 不过RequestTemplate与WebTarget的相似度更高。
 * @author liyibo
 * @date 2026-04-24 14:02
 */
public interface Target<T> {

    /**
     * 此目标所适用的接口类型，例如Route53。
     */
    Class<T> type();

    /**
     * 与该目标关联的配置键，例如route53。
     */
    String name();

    /**
     * 目标的HTTP基础URL，例如https://api/v2
     */
    String url();

    /**
     * 将模板映射到此目标，并添加基础URL以及任何目标特有的头部或查询参数。例如：
     *  public Request apply(RequestTemplate input) {
     *     input.insert(0, url());
     *     input.replaceHeader(“X-Auth”, currentToken);
     *     return input.asRequest();
     *   }
     */
    Request apply(RequestTemplate input);

    class HardCodedTarget<T> implements Target<T> {
        private final Class<T> type;
        private final String name;
        private final String url;

        public HardCodedTarget(Class<T> type, String url) {
            this(type, url, url);
        }

        public HardCodedTarget(Class<T> type, String name, String url) {
            this.type = Util.checkNotNull(type, "type");
            this.name = Util.checkNotNull(Util.emptyToNull(name), "name");
            this.url = Util.checkNotNull(Util.emptyToNull(url), "url");
        }

        @Override
        public Class<T> type() {
            return type;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String url() {
            return url;
        }

        @Override
        public Request apply(RequestTemplate input) {
            if (input.url().indexOf("http") != 0)
                input.target(url());
            return input.request();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof HardCodedTarget other)
                return type.equals(other.type) && name.equals(other.name) && url.equals(other.url);
            return false;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 31 * result + type.hashCode();
            result = 31 * result + name.hashCode();
            result = 31 * result + url.hashCode();
            return result;
        }

        @Override
        public String toString() {
            if (name.equals(url))
                return "HardCodedTarget(type=" + type.getSimpleName() + ", url=" + url + ")";
            return "HardCodedTarget(type=" + type.getSimpleName() + ", name=" + name + ", url=" + url + ")";
        }
    }

    /**
     * 没有url字段的Target实现。
     */
    final class EmptyTarget<T> implements Target<T> {
        private final Class<T> type;
        private final String name;

        EmptyTarget(Class<T> type, String name) {
            this.type = Util.checkNotNull(type, "type");
            this.name = Util.checkNotNull(Util.emptyToNull(name), "name");
        }

        public static <T> EmptyTarget<T> create(Class<T> type) {
            return new EmptyTarget<T>(type, "empty:" + type.getSimpleName());
        }

        public static <T> EmptyTarget<T> create(Class<T> type, String name) {
            return new EmptyTarget<T>(type, name);
        }

        @Override
        public Class<T> type() {
            return type;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String url() {
            throw new UnsupportedOperationException("Empty targets don't have URLs");
        }

        @Override
        public Request apply(RequestTemplate input) {
            if (input.url().indexOf("http") != 0)
                throw new UnsupportedOperationException("Request with non-absolute URL not supported with empty target");
            return input.request();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof EmptyTarget other)
                return type.equals(other.type) && name.equals(other.name);
            return false;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 31 * result + type.hashCode();
            result = 31 * result + name.hashCode();
            return result;
        }

        @Override
        public String toString() {
            if (name.equals("empty:" + type.getSimpleName()))
                return "EmptyTarget(type=" + type.getSimpleName() + ")";
            return "EmptyTarget(type=" + type.getSimpleName() + ", name=" + name + ")";
        }
    }
}
