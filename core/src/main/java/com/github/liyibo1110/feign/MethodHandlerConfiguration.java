package com.github.liyibo1110.feign;

/**
 * @author liyibo
 * @date 2026-04-24 16:02
 */
public class MethodHandlerConfiguration {
    private final MethodMetadata metadata;

    private final Target<?> target;

    private final Retryer retryer;

    private final List<RequestInterceptor> requestInterceptors;

    private final Logger logger;

    private final Logger.Level logLevel;

    private final RequestTemplate.Factory buildTemplateFromArgs;

    private final Request.Options options;

    private final ExceptionPropagationPolicy propagationPolicy;

    public MethodMetadata getMetadata() {
        return metadata;
    }

    public Target<?> getTarget() {
        return target;
    }

    public Retryer getRetryer() {
        return retryer;
    }

    public List<RequestInterceptor> getRequestInterceptors() {
        return requestInterceptors;
    }

    public Logger getLogger() {
        return logger;
    }

    public Logger.Level getLogLevel() {
        return logLevel;
    }

    public RequestTemplate.Factory getBuildTemplateFromArgs() {
        return buildTemplateFromArgs;
    }

    public Request.Options getOptions() {
        return options;
    }

    public ExceptionPropagationPolicy getPropagationPolicy() {
        return propagationPolicy;
    }

    public MethodHandlerConfiguration(MethodMetadata metadata,
                                      Target<?> target,
                                      Retryer retryer,
                                      List<RequestInterceptor> requestInterceptors,
                                      Logger logger,
                                      Logger.Level logLevel,
                                      RequestTemplate.Factory buildTemplateFromArgs,
                                      Request.Options options,
                                      ExceptionPropagationPolicy propagationPolicy) {
        this.target = Util.checkNotNull(target, "target");
        this.retryer = Util.checkNotNull(retryer, "retryer for %s", target);
        this.requestInterceptors = Util.checkNotNull(requestInterceptors, "requestInterceptors for %s", target);
        this.logger = Util.checkNotNull(logger, "logger for %s", target);
        this.logLevel = Util.checkNotNull(logLevel, "logLevel for %s", target);
        this.metadata = Util.checkNotNull(metadata, "metadata for %s", target);
        this.buildTemplateFromArgs = Util.checkNotNull(buildTemplateFromArgs, "metadata for %s", target);
        this.options = Util.checkNotNull(options, "options for %s", target);
        this.propagationPolicy = propagationPolicy;
    }
}
