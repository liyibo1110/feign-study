package com.github.liyibo1110.feign;

/**
 * @author liyibo
 * @date 2026-04-29 17:03
 */
public interface AsyncContextSupplier<C> {
    C newContext();
}
