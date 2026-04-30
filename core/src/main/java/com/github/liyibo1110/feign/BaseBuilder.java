package com.github.liyibo1110.feign;

import com.github.liyibo1110.feign.codec.Codec;
import com.github.liyibo1110.feign.codec.Decoder;
import com.github.liyibo1110.feign.codec.DefaultDecoder;
import com.github.liyibo1110.feign.codec.DefaultEncoder;
import com.github.liyibo1110.feign.codec.DefaultErrorDecoder;
import com.github.liyibo1110.feign.codec.Encoder;
import com.github.liyibo1110.feign.codec.ErrorDecoder;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author liyibo
 * @date 2026-04-24 11:15
 */
public abstract class BaseBuilder<B extends BaseBuilder<B, T>, T> implements Cloneable {

    /** 自身对象 */
    private final B thisB;

    protected List<RequestInterceptor> requestInterceptors = new ArrayList<>();
    protected List<ResponseInterceptor> responseInterceptors = new ArrayList<>();

    protected Logger.Level logLevel = Logger.Level.NONE;

    protected Contract contract = new DefaultContract();
    protected Retryer retryer = new DefaultRetryer();

    protected Logger logger = new Logger.NoOpLogger();

    protected Encoder encoder = new DefaultEncoder();
    protected Decoder decoder = new DefaultDecoder();

    protected boolean closeAfterDecode = true;

    protected boolean decodeVoid = false;

    protected QueryMapEncoder queryMapEncoder = QueryMap.MapEncoder.FIELD.instance();
    protected ErrorDecoder errorDecoder = new DefaultErrorDecoder();
    protected Request.Options options = new Request.Options();

    /** 覆盖Feign内部反射分发的实现方式 */
    protected InvocationHandlerFactory invocationHandlerFactory = new DefaultInvocationHandlerFactory();

    protected boolean dismiss404;
    protected ExceptionPropagationPolicy propagationPolicy = ExceptionPropagationPolicy.NONE;
    protected List<Capability> capabilities = new ArrayList<>();

    public BaseBuilder() {
        super();
        thisB = (B) this;
    }

    public B logLevel(Logger.Level logLevel) {
        this.logLevel = logLevel;
        return thisB;
    }

    public B contract(Contract contract) {
        this.contract = contract;
        return thisB;
    }

    public B retryer(Retryer retryer) {
        this.retryer = retryer;
        return thisB;
    }

    public B logger(Logger logger) {
        this.logger = logger;
        return thisB;
    }

    public B encoder(Encoder encoder) {
        this.encoder = encoder;
        return thisB;
    }

    public B decoder(Decoder decoder) {
        this.decoder = decoder;
        return thisB;
    }

    public B codec(Codec codec) {
        this.encoder = codec.encoder();
        this.decoder = codec.decoder();
        return thisB;
    }

    /**
     * 此标志表示在完成消息解码后，不应自动关闭响应。
     * 如果您计划将响应处理为延迟求值的结构（例如java.util.Iterator），则应设置此标志。
     * Feign标准解码器不支持此标志。
     * 若使用此标志，您必须同时使用自定义解码器，并确保在解码器内部适当位置关闭所有资源（为方便起见，可使用Util.ensureClosed）。
     */
    public B doNotCloseAfterDecode() {
        this.closeAfterDecode = false;
        return thisB;
    }

    public B decodeVoid() {
        this.decodeVoid = true;
        return thisB;
    }

    public B queryMapEncoder(QueryMapEncoder queryMapEncoder) {
        this.queryMapEncoder = queryMapEncoder;
        return thisB;
    }

    public B mapAndDecode(ResponseMapper mapper, Decoder decoder) {
        this.decoder = new Feign.ResponseMappingDecoder(mapper, decoder);
        return thisB;
    }

    /**
     * 此标志表示解码器应处理状态码为404的响应，具体而言，应返回null或空值，而非抛出FeignException。
     * 所有第一方（如gson）解码器都会返回由Util.emptyValueOf定义的已知空值。若需进一步自定义，可包装现有解码器或自行实现。
     *
     * 此标志仅适用于404状态码，而非所有或任意状态码。
     * 这是经过深思熟虑的决定：404 -> 空值这种处理方式既安全又常见，且不会增加重定向、重试或回退策略的复杂性。
     * 如果您的服务器在未找到资源时返回其他状态码，请通过自定义客户端进行修正。
     */
    public B dismiss404() {
        this.dismiss404 = true;
        return thisB;
    }

    @Deprecated
    public B decode404() {
        this.dismiss404 = true;
        return thisB;
    }

    public B errorDecoder(ErrorDecoder errorDecoder) {
        this.errorDecoder = errorDecoder;
        return thisB;
    }

    public B options(Request.Options options) {
        this.options = options;
        return thisB;
    }

    public B requestInterceptor(RequestInterceptor requestInterceptor) {
        this.requestInterceptors.add(requestInterceptor);
        return thisB;
    }

    public B requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
        this.requestInterceptors.clear();
        for (RequestInterceptor requestInterceptor : requestInterceptors)
            this.requestInterceptors.add(requestInterceptor);
        return thisB;
    }

    public B responseInterceptors(Iterable<ResponseInterceptor> responseInterceptors) {
        this.responseInterceptors.clear();
        for (ResponseInterceptor responseInterceptor : responseInterceptors)
            this.responseInterceptors.add(responseInterceptor);
        return thisB;
    }

    public B responseInterceptor(ResponseInterceptor responseInterceptor) {
        this.responseInterceptors.add(responseInterceptor);
        return thisB;
    }

    public B invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
        this.invocationHandlerFactory = invocationHandlerFactory;
        return thisB;
    }

    public B exceptionPropagationPolicy(ExceptionPropagationPolicy propagationPolicy) {
        this.propagationPolicy = propagationPolicy;
        return thisB;
    }

    public B addCapability(Capability capability) {
        this.capabilities.add(capability);
        return thisB;
    }

    @SuppressWarnings("unchecked")
    B enrich() {
        if (capabilities.isEmpty())
            return thisB;

        try {
            B clone = (B) thisB.clone();
            getFieldsToEnrich()
                .forEach(
                    field -> {
                        field.setAccessible(true);
                        try {
                            final Object originalValue = field.get(clone);
                            final Object enriched;
                            if (originalValue instanceof List) {
                                Type ownerType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                                enriched = ((List) originalValue)
                                            .stream()
                                            .map(value -> Capability.enrich(value, (Class<?>) ownerType, capabilities))
                                            .collect(Collectors.toList());
                            } else {
                                enriched = Capability.enrich(originalValue, field.getType(), capabilities);
                            }
                            field.set(clone, enriched);
                        } catch (IllegalArgumentException | IllegalAccessException e) {
                            throw new RuntimeException("Unable to enrich field " + field, e);
                        } finally {
                            field.setAccessible(false);
                        }
                    });

            // enrich each request interceptor, then enrich the list as a whole
            RequestInterceptor[] requestArray = clone.requestInterceptors.toArray(new RequestInterceptor[0]);
            for (int i = 0; i < requestArray.length; i++)
                requestArray[i] = (RequestInterceptor) Capability.enrich(requestArray[i], RequestInterceptor.class, capabilities);

            RequestInterceptors requestInterceptors =
                    (RequestInterceptors) Capability.enrich(new RequestInterceptors(Arrays.asList(requestArray)),
                                                                                    RequestInterceptors.class,
                                                                                    capabilities);
            clone.requestInterceptors = requestInterceptors.interceptors();

            // enrich each response interceptor, then enrich the list as a whole
            ResponseInterceptor[] responseArray = clone.responseInterceptors.toArray(new ResponseInterceptor[0]);
            for (int i = 0; i < responseArray.length; i++)
                responseArray[i] = (ResponseInterceptor) Capability.enrich(responseArray[i], ResponseInterceptor.class, capabilities);

            ResponseInterceptors responseInterceptors =
                    (ResponseInterceptors) Capability.enrich(new ResponseInterceptors(Arrays.asList(responseArray)),
                                                                                      ResponseInterceptors.class,
                                                                                      capabilities);
            clone.responseInterceptors = responseInterceptors.interceptors();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    List<Field> getFieldsToEnrich() {
        return Util.allFields(getClass()).stream()
                // exclude anything generated by compiler
                .filter(field -> !field.isSynthetic())
                // and capabilities itself
                .filter(field -> !Objects.equals(field.getName(), "capabilities"))
                // and thisB helper field
                .filter(field -> !Objects.equals(field.getName(), "thisB"))
                // interceptor lists are enriched per-element then as a whole via custom types
                .filter(field -> !Objects.equals(field.getName(), "requestInterceptors"))
                .filter(field -> !Objects.equals(field.getName(), "responseInterceptors"))
                // skip primitive types
                .filter(field -> !field.getType().isPrimitive())
                // skip enumerations
                .filter(field -> !field.getType().isEnum())
                .collect(Collectors.toList());
    }

    public final T build() {
        return enrich().internalBuild();
    }

    protected abstract T internalBuild();

    protected ResponseInterceptor.Chain responseInterceptorChain() {
        ResponseInterceptor.Chain endOfChain = ResponseInterceptor.Chain.DEFAULT;
        ResponseInterceptor.Chain executionChain = this.responseInterceptors.stream()
                        .reduce(ResponseInterceptor::andThen)
                        .map(interceptor -> interceptor.apply(endOfChain))
                        .orElse(endOfChain);

        return (ResponseInterceptor.Chain) Capability.enrich(executionChain, ResponseInterceptor.Chain.class, capabilities);
    }
}
