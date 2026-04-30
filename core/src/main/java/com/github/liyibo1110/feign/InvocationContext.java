package com.github.liyibo1110.feign;

import com.github.liyibo1110.feign.codec.DecodeException;
import com.github.liyibo1110.feign.codec.Decoder;
import com.github.liyibo1110.feign.codec.ErrorDecoder;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * @author liyibo
 * @date 2026-04-28 17:23
 */
public class InvocationContext {

    private static final long MAX_RESPONSE_BUFFER_SIZE = 8192L;
    private final String configKey;
    private final Decoder decoder;
    private final ErrorDecoder errorDecoder;
    private final boolean dismiss404;
    private final boolean closeAfterDecode;
    private final boolean decodeVoid;
    private final Response response;
    private final Type returnType;

    InvocationContext(String configKey,
                      Decoder decoder,
                      ErrorDecoder errorDecoder,
                      boolean dismiss404,
                      boolean closeAfterDecode,
                      boolean decodeVoid,
                      Response response,
                      Type returnType) {
        this.configKey = configKey;
        this.decoder = decoder;
        this.errorDecoder = errorDecoder;
        this.dismiss404 = dismiss404;
        this.closeAfterDecode = closeAfterDecode;
        this.decodeVoid = decodeVoid;
        this.response = response;
        this.returnType = returnType;
    }

    public Decoder decoder() {
        return decoder;
    }

    public Type returnType() {
        return returnType;
    }

    public Response response() {
        return response;
    }

    public Object proceed() throws Exception {
        if (returnType == Response.class)
            return disconnectResponseBodyIfNeeded(response);

        try {
            final boolean shouldDecodeResponseBody = (response.status() >= 200 && response.status() < 300)
                                                  || (response.status() == 404 && dismiss404 && !isVoidType(returnType));

            if (!shouldDecodeResponseBody)
                throw decodeError(configKey, response);

            if (isVoidType(returnType) && !decodeVoid) {
                Util.ensureClosed(response.body());
                return null;
            }

            Class<?> rawType = Types.getRawType(returnType);
            if (TypedResponse.class.isAssignableFrom(rawType)) {
                Type bodyType = Types.resolveLastTypeParameter(returnType, TypedResponse.class);
                return TypedResponse.builder(response).body(decode(response, bodyType)).build();
            }

            return decode(response, returnType);
        } finally {
            if (closeAfterDecode)
                Util.ensureClosed(response.body());
        }
    }

    private static Response disconnectResponseBodyIfNeeded(Response response) throws IOException {
        final boolean shouldDisconnectResponseBody = response.body() != null
                                                        && response.body().length() != null
                                                        && response.body().length() <= MAX_RESPONSE_BUFFER_SIZE;
        if (!shouldDisconnectResponseBody)
            return response;

        try {
            final byte[] bodyData = Util.toByteArray(response.body().asInputStream());
            return response.toBuilder().body(bodyData).build();
        } finally {
            Util.ensureClosed(response.body());
        }
    }

    private Object decode(Response response, Type returnType) {
        try {
            return decoder.decode(response, returnType);
        } catch (final FeignException e) {
            throw e;
        } catch (final RuntimeException e) {
            throw new DecodeException(response.status(), e.getMessage(), response.request(), e);
        } catch (IOException e) {
            throw FeignException.errorReading(response.request(), response, e);
        }
    }

    private Exception decodeError(String methodKey, Response response) {
        try {
            return errorDecoder.decode(methodKey, response);
        } finally {
            Util.ensureClosed(response.body());
        }
    }

    private boolean isVoidType(Type returnType) {
        return returnType == Void.class
                || returnType == void.class
                || returnType.getTypeName().equals("kotlin.Unit");
    }
}
