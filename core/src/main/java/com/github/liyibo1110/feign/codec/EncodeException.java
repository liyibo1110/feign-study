package com.github.liyibo1110.feign.codec;

import com.github.liyibo1110.feign.FeignException;
import com.github.liyibo1110.feign.Util;

/**
 * 与javax.websocket.EncodeException类似，当消息编码过程中出现问题时抛出。
 * 请注意EncodeException不是IOException，其cause也未设置为IOException。
 * @author liyibo
 * @date 2026-04-28 17:53
 */
public class EncodeException extends FeignException {
    private static final long serialVersionUID = 1L;

    public EncodeException(String message) {
        super(-1, Util.checkNotNull(message, "message"));
    }

    public EncodeException(String message, Throwable cause) {
        super(-1, message, Util.checkNotNull(cause, "cause"));
    }
}
