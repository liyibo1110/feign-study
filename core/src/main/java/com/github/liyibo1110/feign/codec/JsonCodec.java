package com.github.liyibo1110.feign.codec;

import com.github.liyibo1110.feign.Experimental;

/**
 * @author liyibo
 * @date 2026-04-28 18:06
 */
@Experimental
public interface JsonCodec extends Codec {

    @Override
    JsonEncoder encoder();

    @Override
    JsonDecoder decoder();
}
