package com.github.liyibo1110.feign;

/**
 * 可以配置响应拦截器，用于验证或修改响应头、验证解码对象的业务状态，或处理失败请求的响应。
 * 一旦应用了拦截器，就会调用intercept(InvocationContext, ResponseInterceptor.Chain)，随后对响应进行解码。
 * @author liyibo
 * @date 2026-04-28 17:16
 */
public interface ResponseInterceptor {

    /**
     * 由ResponseHandler在刷新响应后调用，并包裹整个解码过程。
     * 必须手动调用ResponseInterceptor.Chain.next(InvocationContext)，或者手动创建一个新的响应对象。
     * @param invocationContext 正在解码的响应相关信息
     */
    Object intercept(InvocationContext invocationContext, Chain chain) throws Exception;

    default ResponseInterceptor andThen(ResponseInterceptor nextInterceptor) {
        return (ic, chain) -> intercept(ic, nextContext -> nextInterceptor.intercept(nextContext, chain));
    }

    @FunctionalInterface
    interface Chain {
        Chain DEFAULT = InvocationContext::proceed;

        /**
         * 将请求的执行委托给链上的其他节点。
         */
        Object next(InvocationContext context) throws Exception;
    }

    default Chain apply(Chain chain) {
        return request -> intercept(request, chain);
    }
}
