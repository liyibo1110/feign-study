package com.github.liyibo1110.feign;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author liyibo
 * @date 2026-04-27 15:27
 */
@Target({ PARAMETER, FIELD, METHOD })
@Retention(RUNTIME)
public @interface Param {
    String value() default "";

    Class<? extends Expander> expander() default ToStringExpander.class;

    boolean encoded() default false;

    interface Expander {
        /**
         * 将该值展开为字符串，不接受也不返回null。
         */
        String expand(Object value);
    }

    final class ToStringExpander implements Expander {
        @Override
        public String expand(Object value) {
            return value.toString();
        }
    }
}
