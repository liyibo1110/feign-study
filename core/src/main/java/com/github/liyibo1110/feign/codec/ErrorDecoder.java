package com.github.liyibo1110.feign.codec;

import com.github.liyibo1110.feign.Response;
import com.github.liyibo1110.feign.Util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * 允许将异常转换为特定于应用程序的异常，将其转换为限流异常便是此类应用的示例。
 * class IllegalArgumentExceptionOn404Decoder implements ErrorDecoder {
 *
 *   @Override
 *   public Exception decode(String methodKey, Response response) {
 *     if (response.status() == 400)
 *       throw new IllegalArgumentException("bad zone name");
 *     return new ErrorDecoder.Default().decode(methodKey, response);
 *   }
 * }
 *
 * 错误处理：
 * Response.status()不在 2xx范围内的响应被归类为错误，由ErrorDecoder处理。
 * 不过，某些RPC API即使在200状态码下，也会在Response.body()中返回错误信息。
 * 例如，在DynECT API中，任务仍在运行的状态会以200状态码返回，并以JSON格式编码。
 * 当出现此类情况时，应抛出应用程序特有的异常（该异常可能支持重试）。
 *
 * 未找到（Not Found）的语义
 * 在HTTP API中，404（未找到）状态通常具有语义价值。
 * 虽然默认行为是抛出异常，但用户也可以通过feign.Feign.Builder.dismiss404()启用404处理。
 *
 * @author liyibo
 * @date 2026-04-28 18:12
 */
public interface ErrorDecoder {

    Exception decode(String methodKey, Response response);

    @Deprecated
    class Default extends DefaultErrorDecoder {

        public Default() {
            super();
        }

        public Default(Integer maxBodyBytesLength, Integer maxBodyCharsLength) {
            super(maxBodyBytesLength, maxBodyCharsLength);
        }
    }

    class RetryAfterDecoder {
        private final DateTimeFormatter dateTimeFormatter;

        RetryAfterDecoder() {
            this(DateTimeFormatter.RFC_1123_DATE_TIME);
        }

        RetryAfterDecoder(DateTimeFormatter dateTimeFormatter) {
            this.dateTimeFormatter = Util.checkNotNull(dateTimeFormatter, "dateTimeFormatter");
        }

        protected long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        public Long apply(String retryAfter) {
            if (retryAfter == null)
                return null;

            if (retryAfter.matches("^[0-9]+\\.?0*$")) {
                retryAfter = retryAfter.replaceAll("\\.0*$", "");
                long deltaMillis = SECONDS.toMillis(Long.parseLong(retryAfter));
                return currentTimeMillis() + deltaMillis;
            }
            try {
                return ZonedDateTime.parse(retryAfter, dateTimeFormatter).toInstant().toEpochMilli();
            } catch (NullPointerException | DateTimeParseException ignored) {
                return null;
            }
        }
    }
}
