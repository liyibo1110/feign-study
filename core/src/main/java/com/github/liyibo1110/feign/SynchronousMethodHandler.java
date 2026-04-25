package com.github.liyibo1110.feign;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * MethodHandler接口的HTTP方法调用专用实现。
 * 负责把一次接口方法调用，转换成一次同步的HTTP请求，并把HTTP响应解码成接口方法的返回值。
 * @author liyibo
 * @date 2026-04-24 15:38
 */
final class SynchronousMethodHandler implements InvocationHandlerFactory.MethodHandler {

    /** HTTP客户端执行接口，真正负责发送request，并拿回response，可对应不同实现，例如hc5，OkHttp，JDK HttpClient */
    private final Client client;

    /** 将response -> 接口原方法的返回值 */
    private final ResponseHandler responseHandler;

    /** 当前接口方法的执行配置，为的是保持这个实现类的简洁 */
    private final MethodHandlerConfiguration methodHandlerConfiguration;

    private SynchronousMethodHandler(MethodHandlerConfiguration methodHandlerConfiguration,
                                     Client client,
                                     ResponseHandler responseHandler) {
        this.methodHandlerConfiguration = Util.checkNotNull(methodHandlerConfiguration, "methodHandlerConfiguration");
        this.client = Util.checkNotNull(client, "client for %s", methodHandlerConfiguration.getTarget());
        this.responseHandler = responseHandler;
    }

    @Override
    public Object invoke(Object[] argv) throws Throwable {
        // 生成RequestTemplate（Request的半成品版本）
        RequestTemplate template = methodHandlerConfiguration.getBuildTemplateFromArgs().create(argv);
        /**
         * 查找本次调用的Options，类似connectTimeout、readTimeout、followRedirects等配置，Request里面没传，则会用默认的Options
         */
        Options options = findOptions(argv);
        // 生成Retryer组件，clone是因为组件是有状态的，要避免共享
        Retryer retryer = this.methodHandlerConfiguration.getRetryer().clone();
        // 循环执行，因为可能会retry
        while (true) {
            try {
                return executeAndDecode(template, options);
            } catch (RetryableException e) {
                // 说明可以尝试重试
                try {
                    retryer.continueOrPropagate(e); // retry组件判断是否可以重试，不进catch就会最终再次循环
                } catch (RetryableException th) {
                    Throwable cause = th.getCause();
                    // 决定是否拆掉Feign外层的异常包装，直接把底层cause直接返回给用户
                    if (methodHandlerConfiguration.getPropagationPolicy() == UNWRAP && cause != null)
                        throw cause;
                    else
                        throw th;
                }
                // 只是输出log
                if (methodHandlerConfiguration.getLogLevel() != Logger.Level.NONE) {
                    methodHandlerConfiguration.getLogger().logRetry(
                                    methodHandlerConfiguration.getMetadata().configKey(),
                                    methodHandlerConfiguration.getLogLevel());
                }
                continue;
            }
        }
    }

    Object executeAndDecode(RequestTemplate template, Options options) throws Throwable {
        Request request = targetRequest(template);

        if (methodHandlerConfiguration.getLogLevel() != Logger.Level.NONE) {
            methodHandlerConfiguration.getLogger().logRequest(methodHandlerConfiguration.getMetadata().configKey(),
                                                              methodHandlerConfiguration.getLogLevel(),
                                                              request);
        }

        Response response;
        long start = System.nanoTime();
        try {
            // 在这里真正发出了http的请求
            response = client.execute(request, options);
            // ensure the request is set. TODO: remove in Feign 12
            response = response.toBuilder().request(request).requestTemplate(template).build();
        } catch (IOException e) {
            if (methodHandlerConfiguration.getLogLevel() != Logger.Level.NONE) {
                methodHandlerConfiguration.getLogger().logIOException(
                                        methodHandlerConfiguration.getMetadata().configKey(),
                                        methodHandlerConfiguration.getLogLevel(),
                                        e,
                                        elapsedTime(start));
            }
            throw errorExecuting(request, e);
        }

        // 调用responseHandler，把response转换成Java接口原来的返回类型
        long elapsedTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        return responseHandler.handleResponse(
                methodHandlerConfiguration.getMetadata().configKey(), response,
                methodHandlerConfiguration.getMetadata().returnType(), elapsedTime);
    }

    long elapsedTime(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    /**
     * 给原始的RequestTemplate叠加requestInterceptors，最后再发起调用。
     * RequestTemplate -> Request
     */
    Request targetRequest(RequestTemplate template) {
        for (RequestInterceptor interceptor : methodHandlerConfiguration.getRequestInterceptors())
            interceptor.apply(template);
        return methodHandlerConfiguration.getTarget().apply(template);
    }

    Options findOptions(Object[] argv) {
        // 方法没有参数，则使用方法级的默认配置
        if (argv == null || argv.length == 0) {
            return this.methodHandlerConfiguration
                    .getOptions()
                    .getMethodOptions(methodHandlerConfiguration.getMetadata().method().getName());
        }
        // 方法参数如果显式带了Options，优先用这个
        return Stream.of(argv)
                .filter(Options.class::isInstance)
                .map(Options.class::cast)
                .findFirst()
                .orElse(this.methodHandlerConfiguration
                                .getOptions()
                                .getMethodOptions(methodHandlerConfiguration.getMetadata().method().getName()));
    }

    static class Factory implements InvocationHandlerFactory.MethodHandler.Factory<Object> {
        private final Client client;
        private final Retryer retryer;
        private final List<RequestInterceptor> requestInterceptors;
        private final ResponseHandler responseHandler;
        private final Logger logger;
        private final Logger.Level logLevel;
        private final ExceptionPropagationPolicy propagationPolicy;
        private final RequestTemplateFactoryResolver requestTemplateFactoryResolver;
        private final Options options;

        Factory(Client client,
                Retryer retryer,
                List<RequestInterceptor> requestInterceptors,
                ResponseHandler responseHandler,
                Logger logger,
                Logger.Level logLevel,
                ExceptionPropagationPolicy propagationPolicy,
                RequestTemplateFactoryResolver requestTemplateFactoryResolver,
                Options options) {
            this.client = Util.checkNotNull(client, "client");
            this.retryer = Util.checkNotNull(retryer, "retryer");
            this.requestInterceptors = Util.checkNotNull(requestInterceptors, "requestInterceptors");
            this.responseHandler = Util.checkNotNull(responseHandler, "responseHandler");
            this.logger = Util.checkNotNull(logger, "logger");
            this.logLevel = Util.checkNotNull(logLevel, "logLevel");
            this.propagationPolicy = propagationPolicy;
            this.requestTemplateFactoryResolver = Util.checkNotNull(requestTemplateFactoryResolver, "requestTemplateFactoryResolver");
            this.options = Util.checkNotNull(options, "options");
        }

        @Override
        public InvocationHandlerFactory.MethodHandler create(Target<?> target, MethodMetadata md, Object requestContext) {
            final RequestTemplate.Factory buildTemplateFromArgs = requestTemplateFactoryResolver.resolve(target, md);
            MethodHandlerConfiguration methodHandlerConfiguration =
                    new MethodHandlerConfiguration(
                            md,
                            target,
                            retryer,
                            requestInterceptors,
                            logger,
                            logLevel,
                            buildTemplateFromArgs,
                            options,
                            propagationPolicy);
            return new SynchronousMethodHandler(methodHandlerConfiguration, client, responseHandler);
        }
    }
}
