package com.github.liyibo1110.feign;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author liyibo
 * @date 2026-04-27 15:31
 */
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface HeaderMap {

}
