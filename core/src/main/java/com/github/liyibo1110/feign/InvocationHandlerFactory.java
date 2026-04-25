package com.github.liyibo1110.feign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 生成InvocationHandler对象的工厂。
 * @author liyibo
 * @date 2026-04-24 14:00
 */
public interface InvocationHandlerFactory {

    InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch);

    /**
     * 对应某个Java接口方法，生成的HTTP请求方法的处理器。
     */
    interface MethodHandler {

        /**
         * 发起HTTP请求的入口
         */
        Object invoke(Object[] argv) throws Throwable;

        interface Factory<C> {
            MethodHandler create(Target<?> target, MethodMetadata md, C requestContext);
        }
    }
}
