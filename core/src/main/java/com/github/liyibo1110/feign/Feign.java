package com.github.liyibo1110.feign;

import com.github.liyibo1110.feign.codec.Decoder;
import com.github.liyibo1110.feign.codec.Encoder;
import com.github.liyibo1110.feign.codec.ErrorDecoder;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * Feign的目的是简化针对符合REST风格的HTTP API的开发工作。
 * 在实现上，Feign是一个用于生成特定目标HTTP API的工厂。
 * @author liyibo
 * @date 2026-04-24 10:07
 */
public abstract class Feign {

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 配置键的格式为未解析的see标签。此方法公开了该格式，以便您在需要进行关联时，可以创建与MethodMetadata.configKey()相同的值。
     * 以下是一些编码示例：
     * - Route53：将匹配类route53.Route53
     * - Route53#list()：将匹配方法route53.Route53#list()
     * - Route53#listAt(Marker)：将匹配方法route53.Route53#listAt(Marker)
     * - Route53#listByNameAndType(String, String)：将匹配方法route53.Route53#listAt(String, String)
     * 请注意，键中不应包含空格！
     * @param targetType Feign接口的类型
     * @param method 被调用的方法，该方法存在于该类型或其父类中。
     * @return key
     */
    public static String configKey(Class targetType, Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(targetType.getSimpleName());
        sb.append('#').append(method.getName()).append('(');
        for (Type param : method.getGenericParameterTypes()) {
            param = Types.resolve(targetType, targetType, param);
            sb.append(Types.getRawType(param).getSimpleName()).append(',');
        }
        if (method.getParameterTypes().length > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.append(')').toString();
    }

    @Deprecated
    public static String configKey(Method method) {
        return configKey(method.getDeclaringClass(), method);
    }

    /**
     * 返回一个针对指定目标的HTTP API新实例，该实例由Contract中的注解定义。
     * 应将此结果缓存起来。
     */
    public abstract <T> T newInstance(Target<T> target);

    public static class Builder extends BaseBuilder<Builder, Feign> {
        private Client client = new DefaultClient(null, null);

        @Override
        public Builder logLevel(Logger.Level logLevel) {
            return super.logLevel(logLevel);
        }

        @Override
        public Builder contract(Contract contract) {
            return super.contract(contract);
        }

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        @Override
        public Builder retryer(Retryer retryer) {
            return super.retryer(retryer);
        }

        @Override
        public Builder logger(Logger logger) {
            return super.logger(logger);
        }

        @Override
        public Builder encoder(Encoder encoder) {
            return super.encoder(encoder);
        }

        @Override
        public Builder decoder(Decoder decoder) {
            return super.decoder(decoder);
        }

        @Override
        public Builder queryMapEncoder(QueryMapEncoder queryMapEncoder) {
            return super.queryMapEncoder(queryMapEncoder);
        }

        @Override
        public Builder mapAndDecode(ResponseMapper mapper, Decoder decoder) {
            return super.mapAndDecode(mapper, decoder);
        }

        @Deprecated
        @Override
        public Builder decode404() {
            return super.decode404();
        }

        @Override
        public Builder errorDecoder(ErrorDecoder errorDecoder) {
            return super.errorDecoder(errorDecoder);
        }

        @Override
        public Builder options(Request.Options options) {
            return super.options(options);
        }

        @Override
        public Builder requestInterceptor(RequestInterceptor requestInterceptor) {
            return super.requestInterceptor(requestInterceptor);
        }

        @Override
        public Builder requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
            return super.requestInterceptors(requestInterceptors);
        }

        @Override
        public Builder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
            return super.invocationHandlerFactory(invocationHandlerFactory);
        }

        @Override
        public Builder doNotCloseAfterDecode() {
            return super.doNotCloseAfterDecode();
        }

        @Override
        public Builder decodeVoid() {
            return super.decodeVoid();
        }

        @Override
        public Builder exceptionPropagationPolicy(ExceptionPropagationPolicy propagationPolicy) {
            return super.exceptionPropagationPolicy(propagationPolicy);
        }

        @Override
        public Builder addCapability(Capability capability) {
            return super.addCapability(capability);
        }

        public <T> T target(Class<T> apiType, String url) {
            return target(new Target.HardCodedTarget<>(apiType, url));
        }

        public <T> T target(Target<T> target) {
            return build().newInstance(target);
        }

        @Override
        public Feign internalBuild() {
            final ResponseHandler responseHandler = new ResponseHandler(
                                                        logLevel,
                                                        logger,
                                                        decoder,
                                                        errorDecoder,
                                                        dismiss404,
                                                        closeAfterDecode,
                                                        decodeVoid,
                                                        responseInterceptorChain());
            InvocationHandlerFactory.MethodHandler.Factory<Object> methodHandlerFactory =
                    new SynchronousMethodHandler.Factory(
                            client,
                            retryer,
                            requestInterceptors,
                            responseHandler,
                            logger,
                            logLevel,
                            propagationPolicy,
                            new RequestTemplateFactoryResolver(encoder, queryMapEncoder),
                            options);
            return new ReflectiveFeign<>(contract, methodHandlerFactory, invocationHandlerFactory, () -> null);
        }
    }

    public static class ResponseMappingDecoder implements Decoder {
        private final ResponseMapper mapper;
        private final Decoder delegate;

        public ResponseMappingDecoder(ResponseMapper mapper, Decoder decoder) {
            this.mapper = mapper;
            this.delegate = decoder;
        }

        @Override
        public Object decode(Response response, Type type) throws IOException {
            return delegate.decode(mapper.map(response, type), type);
        }
    }
}
