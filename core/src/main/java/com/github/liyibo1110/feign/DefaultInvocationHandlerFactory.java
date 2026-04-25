package com.github.liyibo1110.feign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * InvocationHandlerFactory接口的默认实现。
 * @author liyibo
 * @date 2026-04-24 14:13
 */
public class DefaultInvocationHandlerFactory implements InvocationHandlerFactory {

    @Override
    public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
        return new ReflectiveFeign.FeignInvocationHandler(target, dispatch);
    }
}
