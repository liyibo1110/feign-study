package com.github.liyibo1110.feign.codec;

import com.github.liyibo1110.feign.RequestTemplate;
import com.github.liyibo1110.feign.Util;

import java.lang.reflect.Type;

/**
 * 将对象编码为HTTP请求正文。与javax.websocket.Encoder类似。当方法参数未标注@Param注解时，应使用 Encoder。例如：
 * @POST
 * @Path("/")
 * void create(User user);
 *
 * 接口实现例子：
 * public class GsonEncoder implements Encoder {
 *   private final Gson gson;
 *
 *   public GsonEncoder(Gson gson) {
 *     this.gson = gson;
 *   }
 *
 *   @Override
 *   public void encode(Object object, Type bodyType, RequestTemplate template) {
 *     template.body(gson.toJson(object, bodyType));
 *   }
 * }
 *
 * Form编码：
 * 如果在feign.MethodMetadata.formParams()中发现了任何参数，它们将被收集并作为一个映射传递给编码器。
 * 示例：以下是一个表单。请注意，请求行中并未使用这些参数。包含username和password键的映射将传递给编码器，且正文类型将为MAP_STRING_WILDCARD。
 *
 * @RequestLine("POST /")
 * Session login(@Param("username") String username, @Param("password") String password);
 *
 * @author liyibo
 * @date 2026-04-28 17:58
 */
public interface Encoder {
    Type MAP_STRING_WILDCARD = Util.MAP_STRING_WILDCARD;

    void encode(Object object, Type bodyType, RequestTemplate template) throws EncodeException;

    @Deprecated
    class Default extends DefaultEncoder {}
}
