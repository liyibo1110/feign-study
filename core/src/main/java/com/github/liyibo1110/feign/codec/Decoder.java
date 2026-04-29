package com.github.liyibo1110.feign.codec;

import com.github.liyibo1110.feign.FeignException;
import com.github.liyibo1110.feign.Response;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * 将 HTTP响应解码为指定类型的单个对象。
 * 当Response.status()处于2xx范围且返回类型既不是void也不是Response时，会调用此方法。
 *
 * 接口实现例子：
 * public class GsonDecoder implements Decoder {
 *   private final Gson gson = new Gson();
 *
 *   @Override
 *   public Object decode(Response response, Type type) throws IOException {
 *     try {
 *       return gson.fromJson(response.body().asReader(), type);
 *     } catch (JsonIOException e) {
 *       if (e.getCause() != null &&
 *           e.getCause() instanceof IOException) {
 *         throw IOException.class.cast(e.getCause());
 *       }
 *       throw e;
 *     }
 *   }
 * }
 *
 * 实现要点：
 * 类型参数将对应由Feign.newInstance(feign.Target)处理的接口的泛型返回类型。
 * 在编写Decoder的实现时，请确保也测试了诸如List<Foo>之类的参数化类型。
 *
 * 关于异常传播的说明
 * Decoder抛出的异常会被包装为DecodeException，除非它们已经是FeignException的子类，且客户端未通过Feign.Builder.dismiss404()进行配置。
 *
 * @author liyibo
 * @date 2026-04-28 18:02
 */
public interface Decoder {

    Object decode(Response response, Type type) throws IOException, DecodeException, FeignException;

    @Deprecated
    class Default extends DefaultDecoder {}
}
