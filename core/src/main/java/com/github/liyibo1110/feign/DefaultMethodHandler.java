package com.github.liyibo1110.feign;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * MethodHandler接口的default方法调用专用实现。
 *
 * 注意这个实现只是用来调用接口的default方法的，不会发起HTTP请求。
 * default方法虽然有方法体，但是不能通过method.invoke(proxy, args)这样调用，这样容易形成递归，所以用到了JDK自带的MethodHandle，
 * 它比Method更底层、更精确的方法调用句柄，可以绕过普通反射调用的限制，直接指向接口的default方法的真实实现代码。
 * @author liyibo
 * @date 2026-04-24 14:14
 */
final class DefaultMethodHandler implements InvocationHandlerFactory.MethodHandler {

    /**
     * 使用基于Java 7 MethodHandle的反射机制。
     * 由于默认方法仅在Java 8 JVM上运行时才存在，因此这不会影响在旧版JVM上的使用。
     *
     * 当Feign升级到Java 7时，请移除@IgnoreJRERequirement注解。
     *
     * 表示：还没有绑定到具体proxy对象的default方法句柄，unbound意思就是没有指定this
     */
    private final MethodHandle unboundHandle;

    /**
     * 在调用bindTo之后，handle实际上就变成了final。
     *
     * 表示：已经绑定到具体proxy对象之后的MethodHandle，也就是调用过bindTo方法，这时才可以真正执行default方法。
     **/
    private MethodHandle handle;

    public DefaultMethodHandler(Method defaultMethod) {
        // 找到default方法所在的接口
        Class<?> declaringClass = defaultMethod.getDeclaringClass();
        try {
            // 获取一个有权限访问default方法的Lookup，Lookup可以理解成：MethodHandle里面的权限对象
            MethodHandles.Lookup lookup = readLookup(declaringClass);
            // 把Method转成MethodHandle
            this.unboundHandle = lookup.unreflectSpecial(defaultMethod, declaringClass);
        } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * 最终拿到一个能访问接口default方法的Lookup，同时兼容JDK / Android等不同环境。
     */
    private MethodHandles.Lookup readLookup(Class<?> declaringClass) throws IllegalAccessException, InvocationTargetException, NoSuchFieldException {
        try {
            return safeReadLookup(declaringClass);
        } catch (NoSuchMethodException e) {
            try {
                return androidLookup(declaringClass);
            } catch (InstantiationException | NoSuchMethodException instantiationException) {
                return legacyReadLookup();
            }
        }
    }

    public MethodHandles.Lookup androidLookup(Class<?> declaringClass)
            throws InstantiationException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup;
        try {
            // Android 9+ double reflection
            Class<?> classReference = Class.class;
            Class<?>[] classType = new Class[] {Class.class};
            Method reflectedGetDeclaredConstructor = classReference.getDeclaredMethod("getDeclaredConstructor", Class[].class);
            reflectedGetDeclaredConstructor.setAccessible(true);
            Constructor<?> someHiddenMethod = (Constructor<?>) reflectedGetDeclaredConstructor.invoke(MethodHandles.Lookup.class, (Object) classType);
            lookup = (MethodHandles.Lookup) someHiddenMethod.newInstance(declaringClass);
        } catch (IllegalAccessException ex0) {
            // Android < 9 reflection
            Constructor<MethodHandles.Lookup> lookupConstructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class);
            lookupConstructor.setAccessible(true);
            lookup = lookupConstructor.newInstance(declaringClass);
        }
        return (lookup);
    }

    private MethodHandles.Lookup safeReadLookup(Class<?> declaringClass)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        Object privateLookupIn = MethodHandles.class
                        .getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class)
                        .invoke(null, declaringClass, lookup);
        return (MethodHandles.Lookup) privateLookupIn;
    }

    private MethodHandles.Lookup legacyReadLookup() throws NoSuchFieldException, IllegalAccessException {
        Field field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        field.setAccessible(true);
        MethodHandles.Lookup lookup = (MethodHandles.Lookup) field.get(null);
        return lookup;
    }

    /**
     * 重要：把default方法句柄，绑定到最终生成的JDK代理对象上。
     */
    public void bindTo(Object proxy) {
        if (handle != null)
            throw new IllegalStateException("Attempted to rebind a default method handler that was already bound");
        handle = unboundHandle.bindTo(proxy);
    }

    @Override
    public Object invoke(Object[] argv) throws Throwable {
        if (handle == null)
            throw new IllegalStateException("Default method handler invoked before proxy has been bound.");
        // 最终正常执行default方法
        return handle.invokeWithArguments(argv);
    }
}
