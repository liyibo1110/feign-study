package com.github.liyibo1110.feign;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 不生成HTTP请求的方法。
 * @author liyibo
 * @date 2026-04-27 13:22
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface FeignIgnore {

}
