package com.github.liyibo1110.feign;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author liyibo
 * @date 2026-04-27 15:29
 */
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface QueryMap {

    boolean encoded() default false;

    MapEncoder mapEncoder() default MapEncoder.DEFAULT;

    enum MapEncoder {
        BEAN(new BeanQueryMapEncoder()),
        FIELD(new FieldQueryMapEncoder()),
        DEFAULT(null);

        private QueryMapEncoder mapEncoder;

        MapEncoder(QueryMapEncoder mapEncoder) {
            this.mapEncoder = mapEncoder;
        }

        public QueryMapEncoder instance() {
            return mapEncoder;
        }
    }
}
