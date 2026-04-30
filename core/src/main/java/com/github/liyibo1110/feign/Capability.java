package com.github.liyibo1110.feign;

import com.github.liyibo1110.feign.codec.Decoder;
import com.github.liyibo1110.feign.codec.Encoder;
import com.github.liyibo1110.feign.codec.ErrorDecoder;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

/**
 * Capability将核心Feign组件暴露给实现层，以便在构建客户端时能够对核心的某些部分进行定制。
 * 例如，Capability会获取客户端，对其进行修改，并将修改后的版本反馈给Feign。
 *
 * 职责：允许你在Feign.Builder最终组装Client时，对Client、Encoder、Decoder、Contract、Logger、Retryer、InvocationHandlerFactory等核心组件进行包装、替换或增强。
 * 本质还是装饰器模式。
 * @author liyibo
 * @date 2026-04-29 11:21
 */
public interface Capability {

    /**
     * 核心方法：把一个组件依次交给所有的Capability组件处理，最终得到增强后的组件。
     */
    static Object enrich(Object componentToEnrich, Class<?> capabilityToEnrich, List<Capability> capabilities) {
        return capabilities.stream()
                // invoke each individual capability and feed the result to the next one.
                // This is equivalent to:
                // Capability cap1 = ...;
                // Capability cap2 = ...;
                // Capability cap2 = ...;
                // Contract contract = ...;
                // Contract contract1 = cap1.enrich(contract);
                // Contract contract2 = cap2.enrich(contract1);
                // Contract contract3 = cap3.enrich(contract2);
                // or in a more compact version
                // Contract enrichedContract = cap3.enrich(cap2.enrich(cap1.enrich(contract)));
                .reduce(componentToEnrich,
                        (target, capability) -> invoke(target, capability, capabilityToEnrich),
                        (component, enrichedComponent) -> enrichedComponent);
    }

    /**
     * 1、扫描capability类上的所有public方法（也包括了Capability接口本身的default方法）。
     * 2、找出所有的名为enrich的方法（Capability接口有各种enrich方法，只是参数和返回类型不同）。
     * 3、根据返回类型匹配目标组件的类型。
     * 4、反射调用特定的enrich方法，最终返回修饰后对象。
     */
    static Object invoke(Object target, Capability capability, Class<?> capabilityToEnrich) {
        return Arrays.stream(capability.getClass().getMethods())
                .filter(method -> method.getName().equals("enrich"))
                .filter(method -> method.getReturnType().isAssignableFrom(capabilityToEnrich))
                .findFirst()
                .map(method -> {
                    try {
                        return method.invoke(capability, target);
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                        throw new RuntimeException("Unable to enrich " + target, e);
                    }
                }).orElse(target);
    }

    default Client enrich(Client client) {
        return client;
    }

    /*default AsyncClient<Object> enrich(AsyncClient<Object> client) {
        return client;
    }*/

    default Retryer enrich(Retryer retryer) {
        return retryer;
    }

    default RequestInterceptor enrich(RequestInterceptor requestInterceptor) {
        return requestInterceptor;
    }

    default ResponseInterceptor enrich(ResponseInterceptor responseInterceptor) {
        return responseInterceptor;
    }

    default ResponseInterceptor.Chain enrich(ResponseInterceptor.Chain chain) {
        return chain;
    }

    default Logger enrich(Logger logger) {
        return logger;
    }

    default Logger.Level enrich(Logger.Level level) {
        return level;
    }

    default Contract enrich(Contract contract) {
        return contract;
    }

    default Request.Options enrich(Request.Options options) {
        return options;
    }

    default Encoder enrich(Encoder encoder) {
        return encoder;
    }

    default Decoder enrich(Decoder decoder) {
        return decoder;
    }

    default ErrorDecoder enrich(ErrorDecoder decoder) {
        return decoder;
    }

    default InvocationHandlerFactory enrich(InvocationHandlerFactory invocationHandlerFactory) {
        return invocationHandlerFactory;
    }

    default QueryMapEncoder enrich(QueryMapEncoder queryMapEncoder) {
        return queryMapEncoder;
    }

    /*default AsyncResponseHandler enrich(AsyncResponseHandler asyncResponseHandler) {
        return asyncResponseHandler;
    }*/

    /*default <C> AsyncContextSupplier<C> enrich(AsyncContextSupplier<C> asyncContextSupplier) {
        return asyncContextSupplier;
    }*/

    default MethodInfoResolver enrich(MethodInfoResolver methodInfoResolver) {
        return methodInfoResolver;
    }

    default RequestInterceptors enrich(RequestInterceptors requestInterceptors) {
        return requestInterceptors;
    }

    default ResponseInterceptors enrich(ResponseInterceptors responseInterceptors) {
        return responseInterceptors;
    }
}
