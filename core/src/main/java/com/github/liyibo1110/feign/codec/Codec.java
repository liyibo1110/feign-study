package com.github.liyibo1110.feign.codec;

import com.github.liyibo1110.feign.Experimental;

/**
 * Encoder和Decoder组件的封装。
 * @author liyibo
 * @date 2026-04-28 18:05
 */
@Experimental
public interface Codec {

    Encoder encoder();

    Decoder decoder();
}
