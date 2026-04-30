package com.github.liyibo1110.feign;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

/**
 * 异步环境下会用到。
 * @author liyibo
 * @date 2026-04-29 11:24
 */
@Experimental
public class MethodInfo {
    private final Type underlyingReturnType;
    private final boolean asyncReturnType;

    protected MethodInfo(Type underlyingReturnType, boolean asyncReturnType) {
        this.underlyingReturnType = underlyingReturnType;
        this.asyncReturnType = asyncReturnType;
    }

    MethodInfo(Class<?> targetType, Method method) {
        final Type type = Types.resolve(targetType, targetType, method.getGenericReturnType());

        if (type instanceof ParameterizedType && Types.getRawType(type).isAssignableFrom(CompletableFuture.class)) {
            this.asyncReturnType = true;
            this.underlyingReturnType = ((ParameterizedType) type).getActualTypeArguments()[0];
        } else {
            this.asyncReturnType = false;
            this.underlyingReturnType = type;
        }
    }

    Type underlyingReturnType() {
        return underlyingReturnType;
    }

    boolean isAsyncReturnType() {
        return asyncReturnType;
    }
}
