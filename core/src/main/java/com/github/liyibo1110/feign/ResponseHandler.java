package com.github.liyibo1110.feign;

import com.github.liyibo1110.feign.codec.Decoder;
import com.github.liyibo1110.feign.codec.ErrorDecoder;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * 用于在标准响应处理之上提供同步支持的响应处理程序。
 * @author liyibo
 * @date 2026-04-28 17:26
 */
public class ResponseHandler {

    private final Logger.Level logLevel;
    private final Logger logger;

    private final Decoder decoder;
    private final ErrorDecoder errorDecoder;
    private final boolean dismiss404;
    private final boolean closeAfterDecode;

    private final boolean decodeVoid;

    private final ResponseInterceptor.Chain executionChain;

    public ResponseHandler(Logger.Level logLevel,
                           Logger logger,
                           Decoder decoder,
                           ErrorDecoder errorDecoder,
                           boolean dismiss404,
                           boolean closeAfterDecode,
                           boolean decodeVoid,
                           ResponseInterceptor.Chain executionChain) {
        super();
        this.logLevel = logLevel;
        this.logger = logger;
        this.decoder = decoder;
        this.errorDecoder = errorDecoder;
        this.dismiss404 = dismiss404;
        this.closeAfterDecode = closeAfterDecode;
        this.decodeVoid = decodeVoid;
        this.executionChain = executionChain;
    }

    public Object handleResponse(String configKey, Response response, Type returnType, long elapsedTime) throws Exception {
        try {
            response = logAndRebufferResponseIfNeeded(configKey, response, elapsedTime);
            return executionChain.next(new InvocationContext(configKey,
                                                             decoder,
                                                             errorDecoder,
                                                             dismiss404,
                                                             closeAfterDecode,
                                                             decodeVoid,
                                                             response,
                                                             returnType));
        } catch (final IOException e) {
            if (logLevel != Logger.Level.NONE)
                logger.logIOException(configKey, logLevel, e, elapsedTime);

            throw FeignException.errorReading(response.request(), response, e);
        } catch (Exception e) {
            Util.ensureClosed(response.body());
            throw e;
        }
    }

    private Response logAndRebufferResponseIfNeeded(String configKey, Response response, long elapsedTime) throws IOException {
        if (logLevel == Logger.Level.NONE)
            return response;

        return logger.logAndRebufferResponse(configKey, logLevel, response, elapsedTime);
    }
}
