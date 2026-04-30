package com.github.liyibo1110.feign;

import java.lang.reflect.Method;

/**
 * @author liyibo
 * @date 2026-04-29 11:25
 */
@Experimental
public interface MethodInfoResolver {

    MethodInfo resolve(Class<?> targetType, Method method);
}
