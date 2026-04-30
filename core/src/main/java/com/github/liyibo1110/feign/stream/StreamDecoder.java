package com.github.liyibo1110.feign.stream;

import com.github.liyibo1110.feign.FeignException;
import com.github.liyibo1110.feign.Response;
import com.github.liyibo1110.feign.Util;
import com.github.liyibo1110.feign.codec.Decoder;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 支持流式处理的基于迭代器的Decoder实现。
 *
 * 例子：
 * Feign.builder()
 *     .decoder(StreamDecoder.create(JacksonIteratorDecoder.create()))
 *     .doNotCloseAfterDecode() // Required for streaming
 *     .target(GitHub.class, "https://api.github.com");
 *
 * Feign.builder()
 *     .decoder(StreamDecoder.create(JacksonIteratorDecoder.create(), (r, t) -> "hello world")))
 *     .doNotCloseAfterDecode() // Required for streaming
 *     .target(GitHub.class, "https://api.github.com");
 *
 * interface GitHub {
 *   @RequestLine("GET /repos/{owner}/{repo}/contributors")
 *   Stream  contributors(@Param("owner") String owner, @Param("repo") String repo);
 * }
 *
 * 适配器类型的Decoder实现，本身不直接解析JSON、XML或文本内容，而是把一个能解出Iterator的decoder，包装成一个能返回Stream的decoder。
 * 支持Feign接口方法直接返回Stream<T>，内部先把响应体解码成Iterator<T>，再包装成Stream，并在Stream关闭时能关闭底层响应资源。
 *
 *
 * @author liyibo
 * @date 2026-04-29 10:53
 */
public final class StreamDecoder implements Decoder {
    private final Decoder iteratorDecoder;
    private final Optional<Decoder> delegateDecoder;

    StreamDecoder(Decoder iteratorDecoder, Decoder delegateDecoder) {
        this.iteratorDecoder = iteratorDecoder;
        this.delegateDecoder = Optional.ofNullable(delegateDecoder);
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        if (!isStream(type)) {
            if (!delegateDecoder.isPresent())
                throw new IllegalArgumentException("StreamDecoder supports types other than stream. When type is not stream, the delegate decoder needs to be setting.");
            else
                return delegateDecoder.get().decode(response, type);
        }
        ParameterizedType streamType = (ParameterizedType) type;
        Iterator<?> iterator = (Iterator<?>) iteratorDecoder.decode(response, new IteratorParameterizedType(streamType));

        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false)
                .onClose(() -> {
                            if (iterator instanceof Closeable)
                                Util.ensureClosed((Closeable) iterator);
                            else
                                Util.ensureClosed(response);
                        });
    }

    public static boolean isStream(Type type) {
        if (!(type instanceof ParameterizedType))
            return false;

        ParameterizedType parameterizedType = (ParameterizedType) type;
        return parameterizedType.getRawType().equals(Stream.class);
    }

    public static StreamDecoder create(Decoder iteratorDecoder) {
        return new StreamDecoder(iteratorDecoder, null);
    }

    public static StreamDecoder create(Decoder iteratorDecoder, Decoder delegateDecoder) {
        return new StreamDecoder(iteratorDecoder, delegateDecoder);
    }

    static final class IteratorParameterizedType implements ParameterizedType {

        private final ParameterizedType streamType;

        IteratorParameterizedType(ParameterizedType streamType) {
            this.streamType = streamType;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return streamType.getActualTypeArguments();
        }

        @Override
        public Type getRawType() {
            return Iterator.class;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }
}
