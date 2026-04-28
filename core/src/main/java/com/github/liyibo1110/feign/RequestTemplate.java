package com.github.liyibo1110.feign;

import com.github.liyibo1110.feign.template.UriUtils;

import java.io.Serializable;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * HTTP target的请求构建器。
 * 该类是UriTemplate的变体，除了URI之外，还支持在请求头和查询字符串中使用模板表达式。
 *
 * Feign用来承载HTTP请求模板的可变构建器，里面保存了：
 * 1、method
 * 2、URI模板
 * 3、query模板
 * 4、header模板
 * 5、body模板
 * 6、target等信息
 * 当方法参数传入后，会通过resolve()展开变量，最终通过request()生成不可变的Request对象。
 *
 * 注意会有2种RequestTemplate：
 * 1、MethodMetadata里的RequestTemplate：这是解析注解后得到的原始模板。
 * 2、调用RequestTemplate.Factory.create(argv)得到。
 * @author liyibo
 * @date 2026-04-27 15:45
 */
public final class RequestTemplate implements Serializable {
    private static final Pattern QUERY_STRING_PATTERN = Pattern.compile("(?<!\\{)\\?");

    /** 保存query参数的模板，因为query并不是简单的字符串，可能包含模板变量，例如name={name}、ids={ids} */
    private final Map<String, QueryTemplate> queries = new LinkedHashMap<>();

    /** 保存header模板，注意用的是大小写不敏感的TreeMap */
    private final Map<String, HeaderTemplate> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /**
     * target、fragment、uriTemplate，它们一起组成了URL：
     * 1、target表示目标服务地址，必须是绝对地址：https://api.example.com
     * 2、fragment表示URL片段：/users/1#profile
     * 3、uriTemplate表示相对路径模板，不能是绝对路径：/users/{id}
     */
    private String target;
    private String fragment;
    private UriTemplate uriTemplate;

    /**
     * body相关字段。
     * 1、template表示当前body还是模板状态的body。
     * 2、body字段表示已经明确编码后的请求体。
     */
    private BodyTemplate bodyTemplate;
    private Request.Body body = Request.Body.empty();

    private HttpMethod method;
    private transient Charset charset = Util.UTF_8;

    /** true表示路径变量里的"/"，不被编码成%2F，false表示会被编码成%2F，例如a/b -> a%2Fb */
    private boolean decodeSlash = true;

    /** 控制集合参数展开方式，例如ids=[1,2,3]，可能展开成ids=1&ids=2&ids=3 */
    private CollectionFormat collectionFormat = CollectionFormat.EXPLODED;

    /** 表示这个template是否已经完成了变量的解析工作，只有resolved后才能生成Request */
    private boolean resolved = false;

    /** 反向关联属于哪个MethodMetadata和Target */
    private MethodMetadata methodMetadata;
    private Target<?> feignTarget;

    public RequestTemplate() {
        super();
    }

    private RequestTemplate(String target,
                            String fragment,
                            UriTemplate uriTemplate,
                            BodyTemplate bodyTemplate,
                            HttpMethod method,
                            Charset charset,
                            Request.Body body,
                            boolean decodeSlash,
                            CollectionFormat collectionFormat,
                            MethodMetadata methodMetadata,
                            Target<?> feignTarget) {
        this.target = target;
        this.fragment = fragment;
        this.uriTemplate = uriTemplate;
        this.bodyTemplate = bodyTemplate;
        this.method = method;
        this.charset = charset;
        this.body = body;
        this.decodeSlash = decodeSlash;
        this.collectionFormat = (collectionFormat != null) ? collectionFormat : CollectionFormat.EXPLODED;
        this.methodMetadata = methodMetadata;
        this.feignTarget = feignTarget;
    }

    public static RequestTemplate from(RequestTemplate requestTemplate) {
        RequestTemplate template = new RequestTemplate(
                        requestTemplate.target,
                        requestTemplate.fragment,
                        requestTemplate.uriTemplate,
                        requestTemplate.bodyTemplate,
                        requestTemplate.method,
                        requestTemplate.charset,
                        requestTemplate.body,
                        requestTemplate.decodeSlash,
                        requestTemplate.collectionFormat,
                        requestTemplate.methodMetadata,
                        requestTemplate.feignTarget);

        if (!requestTemplate.queries().isEmpty())
            template.queries.putAll(requestTemplate.queries);

        if (!requestTemplate.headers().isEmpty())
            template.headers.putAll(requestTemplate.headers);

        return template;
    }

    @Deprecated
    public RequestTemplate(RequestTemplate toCopy) {
        Util.checkNotNull(toCopy, "toCopy");
        this.target = toCopy.target;
        this.fragment = toCopy.fragment;
        this.method = toCopy.method;
        this.queries.putAll(toCopy.queries);
        this.headers.putAll(toCopy.headers);
        this.charset = toCopy.charset;
        this.body = toCopy.body;
        this.decodeSlash = toCopy.decodeSlash;
        this.collectionFormat = (toCopy.collectionFormat != null) ? toCopy.collectionFormat : CollectionFormat.EXPLODED;
        this.uriTemplate = toCopy.uriTemplate;
        this.bodyTemplate = toCopy.bodyTemplate;
        this.resolved = false;
        this.methodMetadata = toCopy.methodMetadata;
        this.target = toCopy.target;
        this.feignTarget = toCopy.feignTarget;
    }

    /**
     * 核心方法：把uri、query、header、body里面的{变量}用本次的方法参数值展开，生成一个resolved=true的新Template。
     */
    public RequestTemplate resolve(Map<String, ?> variables) {
        StringBuilder uri = new StringBuilder();

        // 不能直接改自己，要生成新的副本
        RequestTemplate resolved = RequestTemplate.from(this);

        // 正常情况下，Contract会设置URI，这里只是兜底创建空模板。
        if (this.uriTemplate == null)
            this.uriTemplate = UriTemplate.create("", !this.decodeSlash, this.charset);

        // 展开URI模板，例如/users/{id} -> /users/100
        String expanded = this.uriTemplate.expand(variables);
        if (expanded != null)
            uri.append(expanded);

        // 展开Query模板
        if (!this.queries.isEmpty()) {
            // 注意这里清空了resolved副本上的query模板，因为后面只想保留已经展开后的query字符串
            resolved.queries(Collections.emptyMap());
            StringBuilder query = new StringBuilder();

            // 逐个展开，最后拼到URI后面
            Iterator<QueryTemplate> queryTemplates = this.queries.values().iterator();
            while (queryTemplates.hasNext()) {
                QueryTemplate queryTemplate = queryTemplates.next();
                String queryExpanded = queryTemplate.expand(variables);
                if (Util.isNotBlank(queryExpanded)) {
                    query.append(queryExpanded);
                    if (queryTemplates.hasNext())
                        query.append("&");
                }
            }

            String queryString = query.toString();
            if (!queryString.isEmpty()) {
                Matcher queryMatcher = QUERY_STRING_PATTERN.matcher(uri);
                if (queryMatcher.find())
                    uri.append("&");
                else
                    uri.append("?");

                uri.append(queryString);
            }
        }

        // 最终回写resolved的uri里面
        resolved.uri(uri.toString());

        // 展开Header模板
        if (!this.headers.isEmpty()) {
            // 和query的类似，清空原header模板，逐个展开header，把展开结果作为literal header放进去
            resolved.headers(Collections.emptyMap());
            for (HeaderTemplate headerTemplate : this.headers.values()) {
                String header = headerTemplate.expand(variables);
                if (!header.isEmpty())
                    resolved.appendHeader(headerTemplate.getName(), Collections.singletonList(header), true);
            }
        }

        // 展开body模板
        if (this.bodyTemplate != null)
            resolved.body(this.bodyTemplate.expand(variables));

        // 标记resolved
        resolved.resolved = true;
        return resolved;
    }

    @Deprecated
    RequestTemplate resolve(Map<String, ?> unencoded, Map<String, Boolean> alreadyEncoded) {
        return this.resolve(unencoded);
    }

    /**
     * 生成Request对象。
     */
    public Request request() {
        if (!this.resolved)
            throw new IllegalStateException("template has not been resolved.");

        return Request.create(this.method, this.url(), this.headers(), this.body, this);
    }

    @Deprecated
    public RequestTemplate method(String method) {
        Util.checkNotNull(method, "method");
        try {
            this.method = HttpMethod.valueOf(method);
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("Invalid HTTP Method: " + method);
        }
        return this;
    }

    public RequestTemplate method(HttpMethod method) {
        Util.checkNotNull(method, "method");
        this.method = method;
        return this;
    }

    public String method() {
        return (method != null) ? method.name() : null;
    }

    public RequestTemplate decodeSlash(boolean decodeSlash) {
        this.decodeSlash = decodeSlash;
        this.uriTemplate = UriTemplate.create(this.uriTemplate.toString(), !this.decodeSlash, this.charset);
        if (!this.queries.isEmpty()) {
            this.queries.replaceAll((key, queryTemplate) -> QueryTemplate.create(
                                    /* replace the current template with new ones honoring the decode value */
                                    queryTemplate.getName(),
                                    queryTemplate.getValues(),
                                    charset,
                                    collectionFormat,
                                    decodeSlash));
        }
        return this;
    }

    public boolean decodeSlash() {
        return decodeSlash;
    }

    public RequestTemplate collectionFormat(CollectionFormat collectionFormat) {
        this.collectionFormat = collectionFormat;
        return this;
    }

    public CollectionFormat collectionFormat() {
        return collectionFormat;
    }

    @Deprecated
    public RequestTemplate append(CharSequence value) {
        /* proxy to url */
        if (this.uriTemplate != null)
            return this.uri(value.toString(), true);

        return this.uri(value.toString());
    }

    @Deprecated
    public RequestTemplate insert(int pos, CharSequence value) {
        return target(value.toString());
    }

    public RequestTemplate uri(String uri) {
        return this.uri(uri, false);
    }

    public RequestTemplate uri(String uri, boolean append) {
        /* validate and ensure that the url is always a relative one */
        if (UriUtils.isAbsolute(uri))
            throw new IllegalArgumentException("url values must be not be absolute.");

        if (uri == null) {
            uri = "/";
        } else if ((!uri.isEmpty()
                && !uri.startsWith("/")
                && !uri.startsWith("{")
                && !uri.startsWith("?")
                && !uri.startsWith(";"))) {
            /* if the start of the url is a literal, it must begin with a slash. */
            uri = "/" + uri;
        }

        int fragmentIndex = uri.indexOf('#');
        if (fragmentIndex > -1) {
            fragment = uri.substring(fragmentIndex);
            uri = uri.substring(0, fragmentIndex);
        }

        /*
         * templates may provide query parameters. since we want to manage those explicity, we will need
         * to extract those out, leaving the uriTemplate with only the path to deal with.
         */
        Matcher queryMatcher = QUERY_STRING_PATTERN.matcher(uri);
        if (queryMatcher.find()) {
            String queryString = uri.substring(queryMatcher.start() + 1);

            /* parse the query string */
            this.extractQueryTemplates(queryString, append);

            /* reduce the uri to the path */
            uri = uri.substring(0, queryMatcher.start());
        }

        /* replace the uri template */
        if (append && this.uriTemplate != null)
            this.uriTemplate = UriTemplate.append(this.uriTemplate, uri);
        else
            this.uriTemplate = UriTemplate.create(uri, !this.decodeSlash, this.charset);

        return this;
    }

    public RequestTemplate target(String target) {
        /* target can be empty */
        if (Util.isBlank(target))
            return this;

        /* verify that the target contains the scheme, host and port */
        if (!UriUtils.isAbsolute(target))
            throw new IllegalArgumentException("target values must be absolute.");

        if (target.endsWith("/"))
            target = target.substring(0, target.length() - 1);

        try {
            /* parse the target */
            URI targetUri = URI.create(target);

            if (Util.isNotBlank(targetUri.getRawQuery())) {
                /*
                 * target has a query string, we need to make sure that they are recorded as queries
                 */
                this.extractQueryTemplates(targetUri.getRawQuery(), true);
            }

            /* strip the query string */
            this.target = targetUri.getScheme() + "://" + targetUri.getRawAuthority() + targetUri.getRawPath();
            if (targetUri.getFragment() != null)
                this.fragment = "#" + targetUri.getFragment();

        } catch (IllegalArgumentException iae) {
            /* the uri provided is not a valid one, we can't continue */
            throw new IllegalArgumentException("Target is not a valid URI.", iae);
        }
        return this;
    }

    public String url() {
        /* build the fully qualified url with all query parameters */
        StringBuilder url = new StringBuilder(this.path());
        if (!this.queries.isEmpty())
            url.append(this.queryLine());

        if (fragment != null)
            url.append(fragment);

        return url.toString();
    }

    public String path() {
        /* build the fully qualified url with all query parameters */
        StringBuilder path = new StringBuilder();
        if (this.target != null)
            path.append(this.target);

        if (this.uriTemplate != null)
            path.append(this.uriTemplate.toString());

        if (path.length() == 0)
            path.append("/");

        return path.toString();
    }

    public List<String> variables() {
        /* combine the variables from the uri, query, header, and body templates */
        List<String> variables = new ArrayList<>(this.uriTemplate.getVariables());

        /* queries */
        for (QueryTemplate queryTemplate : this.queries.values())
            variables.addAll(queryTemplate.getVariables());

        /* headers */
        for (HeaderTemplate headerTemplate : this.headers.values())
            variables.addAll(headerTemplate.getVariables());

        /* body */
        if (this.bodyTemplate != null)
            variables.addAll(this.bodyTemplate.getVariables());

        return variables;
    }

    public RequestTemplate query(String name, String... values) {
        if (values == null)
            return query(name, Collections.emptyList());

        return query(name, Arrays.asList(values));
    }

    public RequestTemplate query(String name, Iterable<String> values) {
        return appendQuery(name, values, this.collectionFormat);
    }

    public RequestTemplate query(String name, Iterable<String> values, CollectionFormat collectionFormat) {
        return appendQuery(name, values, collectionFormat);
    }

    private RequestTemplate appendQuery(String name, Iterable<String> values, CollectionFormat collectionFormat) {
        if (!values.iterator().hasNext()) {
            /* empty value, clear the existing values */
            this.queries.remove(name);
            return this;
        }

        /* create a new query template out of the information here */
        this.queries.compute(
                name,
                (key, queryTemplate) -> {
                    if (queryTemplate == null)
                        return QueryTemplate.create(name, values, this.charset, collectionFormat, this.decodeSlash);
                    else
                        return QueryTemplate.append(queryTemplate, values, collectionFormat, this.decodeSlash);
                });
        return this;
    }

    public RequestTemplate queries(Map<String, Collection<String>> queries) {
        if (queries == null || queries.isEmpty())
            this.queries.clear();
        else
            queries.forEach(this::query);

        return this;
    }

    public Map<String, Collection<String>> queries() {
        Map<String, Collection<String>> queryMap = new LinkedHashMap<>();
        this.queries.forEach((key, queryTemplate) -> {
                                List<String> values = new ArrayList<>(queryTemplate.getValues());
                                /* add the expanded collection, but lock it */
                                queryMap.put(key, Collections.unmodifiableList(values));
                            });
        return Collections.unmodifiableMap(queryMap);
    }

    public RequestTemplate header(String name, String... values) {
        if (values == null)
            return appendHeader(name, Collections.emptyList());

        return header(name, Arrays.asList(values));
    }

    public RequestTemplate header(String name, Iterable<String> values) {
        if (name == null || name.isEmpty())
            throw new IllegalArgumentException("name is required.");

        if (values == null)
            values = Collections.emptyList();

        return appendHeader(name, values);
    }

    public RequestTemplate headerLiteral(String name, String... values) {
        if (values == null)
            return headerLiteral(name, Collections.emptyList());

        return headerLiteral(name, Arrays.asList(values));
    }

    public RequestTemplate headerLiteral(String name, Iterable<String> values) {
        if (name == null || name.isEmpty())
            throw new IllegalArgumentException("name is required.");

        if (values == null)
            values = Collections.emptyList();

        return appendHeader(name, values, true);
    }

    public RequestTemplate removeHeader(String name) {
        if (name == null || name.isEmpty())
            throw new IllegalArgumentException("name is required.");

        this.headers.remove(name);
        return this;
    }

    private RequestTemplate appendHeader(String name, Iterable<String> values) {
        return this.appendHeader(name, values, false);
    }

    private RequestTemplate appendHeader(String name, Iterable<String> values, boolean literal) {
        if (!values.iterator().hasNext()) {
            /* empty value, clear the existing values */
            this.headers.remove(name);
            return this;
        }
        if (name.equals("Content-Type")) {
            // a client can only produce content of one single type, so always override Content-Type and
            // only add a single type
            this.headers.remove(name);
            this.headers.put(name, HeaderTemplate.create(name, Collections.singletonList(values.iterator().next())));
            return this;
        }
        this.headers.compute(
                name,
                (headerName, headerTemplate) -> {
                    if (headerTemplate == null) {
                        if (literal)
                            return HeaderTemplate.literal(headerName, values);
                        else
                            return HeaderTemplate.create(headerName, values);

                    } else if (literal) {
                        return HeaderTemplate.appendLiteral(headerTemplate, values);
                    } else {
                        return HeaderTemplate.append(headerTemplate, values);
                    }
                });
        return this;
    }

    public RequestTemplate headers(Map<String, Collection<String>> headers) {
        if (headers != null && !headers.isEmpty())
            headers.forEach(this::header);
        else
            this.headers.clear();

        return this;
    }

    public Map<String, Collection<String>> headers() {
        Map<String, Collection<String>> headerMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        this.headers.forEach(
                (key, headerTemplate) -> {
                    List<String> values = new ArrayList<>(headerTemplate.getValues());
                    /* add the expanded collection, but only if it has values */
                    if (!values.isEmpty())
                        headerMap.put(key, values);
                });
        return headerMap;
    }

    public RequestTemplate body(byte[] data, Charset charset) {
        this.body(Request.Body.create(data, charset));
        return this;
    }

    public RequestTemplate body(String bodyText) {
        this.body(Request.Body.create(bodyText.getBytes(this.charset), this.charset));
        return this;
    }

    @Deprecated
    public RequestTemplate body(Request.Body body) {
        this.body = body;

        /* body template must be cleared to prevent double processing */
        this.bodyTemplate = null;

        header(Util.CONTENT_LENGTH, Collections.emptyList());
        if (body.length() > 0)
            header(Util.CONTENT_LENGTH, String.valueOf(body.length()));

        return this;
    }

    public Charset requestCharset() {
        if (this.body != null)
            return this.body.getEncoding().orElse(this.charset);

        return this.charset;
    }

    public byte[] body() {
        return body.asBytes();
    }

    @Deprecated
    public Request.Body requestBody() {
        return this.body;
    }

    public RequestTemplate bodyTemplate(String bodyTemplate) {
        this.bodyTemplate = BodyTemplate.create(bodyTemplate, this.charset);
        return this;
    }

    public RequestTemplate bodyTemplate(String bodyTemplate, Charset charset) {
        this.bodyTemplate = BodyTemplate.create(bodyTemplate, charset);
        this.charset = charset;
        return this;
    }

    public String bodyTemplate() {
        if (this.bodyTemplate != null)
            return this.bodyTemplate.toString();
        return null;
    }

    @Override
    public String toString() {
        return request().toString();
    }

    public boolean hasRequestVariable(String variable) {
        return this.getRequestVariables().contains(variable);
    }

    public Collection<String> getRequestVariables() {
        final Collection<String> variables = new LinkedHashSet<>(this.uriTemplate.getVariables());
        this.queries.values().forEach(queryTemplate -> variables.addAll(queryTemplate.getVariables()));
        this.headers.values().forEach(headerTemplate -> variables.addAll(headerTemplate.getVariables()));
        return variables;
    }

    public boolean resolved() {
        return this.resolved;
    }

    public String queryLine() {
        StringBuilder queryString = new StringBuilder();

        if (!this.queries.isEmpty()) {
            Iterator<QueryTemplate> iterator = this.queries.values().iterator();
            while (iterator.hasNext()) {
                QueryTemplate queryTemplate = iterator.next();
                String query = queryTemplate.toString();
                if (query != null && !query.isEmpty()) {
                    queryString.append(query);
                    if (iterator.hasNext())
                        queryString.append("&");
                }
            }
        }
        /* remove any trailing ampersands */
        String result = queryString.toString();
        if (result.endsWith("&"))
            result = result.substring(0, result.length() - 1);

        if (!result.isEmpty())
            result = "?" + result;

        return result;
    }

    private void extractQueryTemplates(String queryString, boolean append) {
        /* split the query string up into name value pairs */
        Map<String, List<String>> queryParameters =
                Arrays.stream(queryString.split("&"))
                        .map(this::splitQueryParameter)
                        .collect(Collectors.groupingBy(
                                        AbstractMap.SimpleImmutableEntry::getKey,
                                        LinkedHashMap::new,
                                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        /* add them to this template */
        if (!append) {
            /* clear the queries and use the new ones */
            this.queries.clear();
        }
        queryParameters.forEach(this::query);
    }

    private AbstractMap.SimpleImmutableEntry<String, String> splitQueryParameter(String pair) {
        int eq = pair.indexOf("=");
        final String name = (eq > 0) ? pair.substring(0, eq) : pair;
        final String value = (eq > 0 && eq < pair.length()) ? pair.substring(eq + 1) : null;
        return new AbstractMap.SimpleImmutableEntry<>(name, value);
    }

    @Experimental
    public RequestTemplate methodMetadata(MethodMetadata methodMetadata) {
        this.methodMetadata = methodMetadata;
        return this;
    }

    @Experimental
    public RequestTemplate feignTarget(Target<?> feignTarget) {
        this.feignTarget = feignTarget;
        return this;
    }

    @Experimental
    public MethodMetadata methodMetadata() {
        return methodMetadata;
    }

    @Experimental
    public Target<?> feignTarget() {
        return feignTarget;
    }

    interface Factory {
        RequestTemplate create(Object[] argv);
    }
}
