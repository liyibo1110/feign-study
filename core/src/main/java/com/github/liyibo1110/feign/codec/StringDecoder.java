package com.github.liyibo1110.feign.codec;

import com.github.liyibo1110.feign.Response;
import com.github.liyibo1110.feign.Util;

import java.io.IOException;
import java.lang.reflect.Type;

import static java.lang.String.format;

/**
 * Decoder的实现类，只能支持响应body是String的类型。
 * @author liyibo
 * @date 2026-04-28 18:07
 */
public class StringDecoder implements Decoder {

    @Override
    public Object decode(Response response, Type type) throws IOException {
        Response.Body body = response.body();
        if (response.status() == 404 || response.status() == 204 || body == null)
            return null;

        if (String.class.equals(type))
            return Util.toString(body.asReader(Util.UTF_8));

        throw new DecodeException(response.status(),
                                  format("%s is not a type supported by this decoder.", type),
                                  response.request());
    }
}
