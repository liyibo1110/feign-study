package com.github.liyibo1110.feign.codec;

import com.github.liyibo1110.feign.Response;
import com.github.liyibo1110.feign.Util;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Decoder接口的默认实现，只能支持响应body是String或byte[]的类型。
 * @author liyibo
 * @date 2026-04-28 18:10
 */
public class DefaultDecoder extends StringDecoder {

    @Override
    public Object decode(Response response, Type type) throws IOException {
        if (response.status() == 404 || response.status() == 204)
            return Util.emptyValueOf(type);
        if (response.body() == null)
            return null;

        if (byte[].class.equals(type))
            return Util.toByteArray(response.body().asInputStream());

        return super.decode(response, type);
    }
}
