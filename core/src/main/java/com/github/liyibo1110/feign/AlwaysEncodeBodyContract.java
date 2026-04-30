package com.github.liyibo1110.feign;

/**
 * DeclarativeContract 扩展允许用户提供的自定义编码器仅使用请求模板和方法参数来定义请求消息的有效载荷，而无需特定的、唯一的正文对象。
 *
 * 当应用程序需要一个Feign客户端，且该客户端的请求有效载荷完全由自定义Feign编码器定义（无论客户端方法声明了多少个参数）时，此类契约非常有用。
 * 在这种情况下，即使没有body参数，提供的编码器也必须知道如何定义请求负载（例如，基于方法名称、方法返回类型以及自定义注解提供的其他元数据，
 * 所有这些信息均可通过提供的RequestTemplate对象获取）。
 * @author liyibo
 * @date 2026-04-29 11:18
 */
public abstract class AlwaysEncodeBodyContract extends DeclarativeContract {

}
