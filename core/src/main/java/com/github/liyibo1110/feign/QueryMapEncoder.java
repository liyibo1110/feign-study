package com.github.liyibo1110.feign;

import java.util.Map;

/**
 * 把一个普通的Java对象转换成Map<String, Object>，然后Feign再把这个Map展开成URL的query参数。
 * @RequestLine("GET /users")
 * List<User> query(@QueryMap UserQuery query);
 * 调用：api.query(new UserQuery("tom", 18));
 * 变成：GET /users?name=tom&age=18
 * 即：UserQuery -> Map<String, Object>，然后RequestTemplate或者QueryTemplate再把这个Map拼成query字符串。
 *
 * 所以它只是用来处理url地址的，并不涉及request body。
 * @author liyibo
 * @date 2026-04-29 10:11
 */
public interface QueryMapEncoder {

    /**
     * Object -> Map<String, Object>
     */
    Map<String, Object> encode(Object object);
}
