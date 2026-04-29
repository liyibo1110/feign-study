package com.github.liyibo1110.feign.codec;

import com.github.liyibo1110.feign.FeignException;
import com.github.liyibo1110.feign.Request;
import com.github.liyibo1110.feign.Util;

/**
 * 与javax.websocket.DecodeException类似，当消息解码过程中出现问题时抛出。
 *  请注意DncodeException不是IOException，其cause也未设置为IOException。
 * @author liyibo
 * @date 2026-04-28 17:57
 */
public class DecodeException extends FeignException {
    private static final long serialVersionUID = 1L;

    public DecodeException(int status, String message, Request request) {
        super(status, Util.checkNotNull(message, "message"), request);
    }

    public DecodeException(int status, String message, Request request, Throwable cause) {
        super(status, message, request, Util.checkNotNull(cause, "cause"));
    }
}
