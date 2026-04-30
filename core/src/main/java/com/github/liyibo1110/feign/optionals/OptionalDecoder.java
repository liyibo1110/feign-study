package com.github.liyibo1110.feign.optionals;

import com.github.liyibo1110.feign.Response;
import com.github.liyibo1110.feign.Util;
import com.github.liyibo1110.feign.codec.Decoder;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Optional;

/**
 * @author liyibo
 * @date 2026-04-29 10:03
 */
public final class OptionalDecoder implements Decoder {

    final Decoder delegate;

    public OptionalDecoder(Decoder delegate) {
        Objects.requireNonNull(delegate, "Decoder must not be null. ");
        this.delegate = delegate;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
        if (!isOptional(type))
            return delegate.decode(response, type);
        // 如果type是Optional类型
        if (response.status() == 404 || response.status() == 204)
            return Optional.empty();
        Type enclosedType = Util.resolveLastTypeParameter(type, Optional.class);
        // 先decode，然后重新包装成Optional返回
        return Optional.ofNullable(delegate.decode(response, enclosedType));
    }

    /**
     * 判断type是不是Optional类型。
     */
    static boolean isOptional(Type type) {
        if (!(type instanceof ParameterizedType))
            return false;

        ParameterizedType parameterizedType = (ParameterizedType) type;
        return parameterizedType.getRawType().equals(Optional.class);
    }
}
