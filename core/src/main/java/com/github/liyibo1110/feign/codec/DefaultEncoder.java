package com.github.liyibo1110.feign.codec;

import com.github.liyibo1110.feign.RequestTemplate;

import java.lang.reflect.Type;

import static java.lang.String.format;

/**
 * Encoder接口的默认实现，只能处理String和byte[]类型的body。
 * @author liyibo
 * @date 2026-04-28 18:08
 */
public class DefaultEncoder implements Encoder {

    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) {
        if (bodyType == String.class)
            template.body(object.toString());
        else if (bodyType == byte[].class)
            template.body((byte[]) object, null);
        else if (object != null)
            throw new EncodeException(format("%s is not a type supported by this encoder.", object.getClass()));
    }
}
