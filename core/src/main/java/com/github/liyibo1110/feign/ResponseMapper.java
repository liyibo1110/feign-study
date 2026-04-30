package com.github.liyibo1110.feign;

import java.lang.reflect.Type;

/**
 * @author liyibo
 * @date 2026-04-29 11:13
 */
public interface ResponseMapper {

    Response map(Response response, Type type);
}
