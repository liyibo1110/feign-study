package com.github.liyibo1110.feign;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author liyibo
 * @date 2026-04-27 15:16
 */
@Target({METHOD, TYPE})
@Retention(RUNTIME)
public @interface Headers {

    String[] value();
}
