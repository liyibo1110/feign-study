package com.github.liyibo1110.feign;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * 会在适当的时候返回Location标头值的response拦截器。
 * @author liyibo
 * @date 2026-04-29 11:25
 */
public class RedirectionInterceptor implements ResponseInterceptor {

    @Override
    public Object intercept(InvocationContext invocationContext, Chain chain) throws Exception {
        Response response = invocationContext.response();
        int status = response.status();
        Object returnValue = null;
        if (300 <= status && status < 400 && response.headers().containsKey("Location")) {
            Type returnType = rawType(invocationContext.returnType());
            Collection<String> locations = response.headers().get("Location");
            if (Collection.class.equals(returnType)) {
                returnValue = locations;
            } else if (String.class.equals(returnType)) {
                if (locations.isEmpty())
                    returnValue = "";
                else
                    returnValue = locations.stream().findFirst().orElse("");
            }
        }
        if (returnValue == null) {
            return chain.next(invocationContext);
        } else {
            response.close();
            return returnValue;
        }
    }

    private Type rawType(Type type) {
        return type instanceof ParameterizedType ? ((ParameterizedType) type).getRawType() : type;
    }
}
