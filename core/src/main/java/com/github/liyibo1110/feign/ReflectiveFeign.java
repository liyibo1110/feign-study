package com.github.liyibo1110.feign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 职责：把一个Java接口，转换成一个动态代理对象，并把接口里的方法绑定到对应的MethodHandler组件上。
 * 1、解析接口：Contract -> MethodMetadata
 * 2、给每个方法创建执行器：MethodHandler
 * 3、组装一个JDK Proxy：InvocationHandler
 * @author liyibo
 * @date 2026-04-24 11:29
 */
public class ReflectiveFeign<C> extends Feign {

    /**
     * 把接口方法解析成MethodHandler的工厂和管理器。
     * 1、用Contract解析接口方法，生成MethodMetadata。
     * 2、用MethodHandler.Factory来创建MethodHandler。
     **/
    private final ParseHandlersByName<C> targetToHandlersByName;

    /**
     * 把target和methodToHandlerMap，转成InvocationHandler，即：
     * InvocationHandler handler = factory.create(target, methodToHandler);
     * MethodHandler：每个方法的执行逻辑。
     * InvocationHandler：统一的分发入口（类似Dispatcher）。
     */
    private final InvocationHandlerFactory factory;

    /**
     * 异步的上下文提供器，在版本13开始支持async，因此这个context用于：
     * 1、request-scoped数据
     * 2、trace / span
     * 3、async执行上下文
     * 可以理解成：为一次调用提供的上下文对象（类似ThreadLocal的抽象替代）。
     */
    private final AsyncContextSupplier<C> defaultContextSupplier;

    ReflectiveFeign(Contract contract,
                    InvocationHandlerFactory.MethodHandler.Factory<C> methodHandlerFactory,
                    InvocationHandlerFactory invocationHandlerFactory,
                    AsyncContextSupplier<C> defaultContextSupplier) {
        this.targetToHandlersByName = new ParseHandlersByName<C>(contract, methodHandlerFactory);
        this.factory = invocationHandlerFactory;
        this.defaultContextSupplier = defaultContextSupplier;
    }

    /**
     * 创建与目标的API绑定。由于此操作会调用反射，因此应注意缓存结果。
     */
    public <T> T newInstance(Target<T> target) {
        return newInstance(target, defaultContextSupplier.newContext());
    }

    @SuppressWarnings("unchecked")
    public <T> T newInstance(Target<T> target, C requestContext) {
        /**
         * 步骤一：验证接口的合法性
         * 1、必须是接口。
         * 2、CompletableFuture限制：返回值必须是CompletableFuture、必须带泛型、不能是wildcard。
         */
        TargetSpecificationVerifier.verify(target);

        /**
         * 步骤二：解析方法，生成MethodHandler
         */
        Map<Method, InvocationHandlerFactory.MethodHandler> methodToHandler = targetToHandlersByName.apply(target, requestContext);

        /**
         * 步骤三：创建InvocationHandler，create会生成FeignInvocationHandler对象
         */
        InvocationHandler handler = factory.create(target, methodToHandler);

        /**
         * 步骤四：创建JDK Proxy，最终返回的就是这个
         */
        T proxy = (T) Proxy.newProxyInstance(target.type().getClassLoader(), new Class<?>[] {target.type()}, handler);

        /**
         * 步骤五：绑定default method，为了让default方法可以：
         * 1、通过MethodHandle绑定到proxy。
         * 2、实现真正调用接口的默认实现。
         */
        for (InvocationHandlerFactory.MethodHandler methodHandler : methodToHandler.values()) {
            if (methodHandler instanceof DefaultMethodHandler)
                ((DefaultMethodHandler) methodHandler).bindTo(proxy);
        }

        return proxy;
    }

    static class FeignInvocationHandler implements InvocationHandler {
        private final Target target;
        private final Map<Method, InvocationHandlerFactory.MethodHandler> dispatch;

        FeignInvocationHandler(Target target, Map<Method, InvocationHandlerFactory.MethodHandler> dispatch) {
            this.target = Util.checkNotNull(target, "target");
            this.dispatch = Util.checkNotNull(dispatch, "dispatch for %s", target);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("equals".equals(method.getName())) {
                try {
                    Object otherHandler = args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
                    return equals(otherHandler);
                } catch (IllegalArgumentException e) {
                    return false;
                }
            } else if ("hashCode".equals(method.getName())) {
                return hashCode();
            } else if ("toString".equals(method.getName())) {
                return toString();
            } else if (!dispatch.containsKey(method)) {
                throw new UnsupportedOperationException(String.format("Method \"%s\" should not be called", method.getName()));
            }
            // 上面都不符合要求，这里才走正常的http调用（执行MethodHandler.invoke）
            return dispatch.get(method).invoke(args);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof FeignInvocationHandler other)
                return target.equals(other.target);
            return false;
        }

        @Override
        public int hashCode() {
            return target.hashCode();
        }

        @Override
        public String toString() {
            return target.toString();
        }
    }

    private static final class ParseHandlersByName<C> {
        private final Contract contract;
        private final InvocationHandlerFactory.MethodHandler.Factory<C> factory;

        ParseHandlersByName(Contract contract, InvocationHandlerFactory.MethodHandler.Factory<C> factory) {
            this.contract = contract;
            this.factory = factory;
        }

        /**
         * 生成接口所有方法的到MethodHandler映射（主要通过Contract组件）。
         */
        public Map<Method, InvocationHandlerFactory.MethodHandler> apply(Target target, C requestContext) {
            final Map<Method, InvocationHandlerFactory.MethodHandler> result = new LinkedHashMap<>();

            // 将接口方法，生成MethodMetadata集合
            final List<MethodMetadata> metadataList = contract.parseAndValidateMetadata(target.type());
            for (MethodMetadata md : metadataList) {
                final Method method = md.method();
                if (method.getDeclaringClass() == Object.class)
                    continue;
                final InvocationHandlerFactory.MethodHandler handler = createMethodHandler(target, md, requestContext);
                result.put(method, handler);
            }

            for (Method method : target.type().getMethods()) {
                if (Util.isDefault(method)) {
                    final InvocationHandlerFactory.MethodHandler handler = new DefaultMethodHandler(method);
                    result.put(method, handler);
                }
            }

            return result;
        }

        private InvocationHandlerFactory.MethodHandler createMethodHandler(final Target<?> target, final MethodMetadata md, final C requestContext) {
            if (md.isIgnored()) {
                return args -> {
                    throw new IllegalStateException(md.configKey() + " is not a method handled by feign");
                };
            }

            return factory.create(target, md, requestContext);
        }
    }

    private static class TargetSpecificationVerifier {

        public static <T> void verify(Target<T> target) {
            Class<T> type = target.type();
            if (!type.isInterface())
                throw new IllegalArgumentException("Type must be an interface: " + type);

            for (final Method m : type.getMethods()) {
                final Class<?> retType = m.getReturnType();

                if (!CompletableFuture.class.isAssignableFrom(retType))
                    continue; // synchronous case

                if (retType != CompletableFuture.class)
                    throw new IllegalArgumentException("Method return type is not CompleteableFuture: " + getFullMethodName(type, retType, m));

                final Type genRetType = m.getGenericReturnType();

                if (!(genRetType instanceof ParameterizedType))
                    throw new IllegalArgumentException("Method return type is not parameterized: " + getFullMethodName(type, genRetType, m));

                if (((ParameterizedType) genRetType).getActualTypeArguments()[0] instanceof WildcardType)
                    throw new IllegalArgumentException("Wildcards are not supported for return-type parameters: " + getFullMethodName(type, genRetType, m));
            }
        }

        private static String getFullMethodName(Class<?> type, Type retType, Method m) {
            return retType.getTypeName() + " " + type.toGenericString() + "." + m.getName();
        }
    }
}
