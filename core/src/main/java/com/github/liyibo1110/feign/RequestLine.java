package com.github.liyibo1110.feign;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author liyibo
 * @date 2026-04-27 15:17
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface RequestLine {

    String value();

    boolean decodeSlash() default true;

    CollectionFormat collectionFormat() default CollectionFormat.EXPLODED;
}
