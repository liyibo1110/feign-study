package com.github.liyibo1110.feign.codec;

import com.github.liyibo1110.feign.Experimental;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * @author liyibo
 * @date 2026-04-28 18:06
 */
@Experimental
public interface JsonDecoder extends Decoder {

    Object convert(Object object, Type type) throws IOException;
}
