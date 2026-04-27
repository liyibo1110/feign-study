package com.github.liyibo1110.feign;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 负责：将Method转换成MethodMetadata
 * @author liyibo
 * @date 2026-04-24 16:47
 */
public interface Contract {

    /**
     * Class -> List<MethodMetadata>
     */
    List<MethodMetadata> parseAndValidateMetadata(Class<?> targetType);

    abstract class BaseContract implements Contract {
        @Override
        public List<MethodMetadata> parseAndValidateMetadata(Class<?> targetType) {
            // 接口上不能有泛型参数
            Util.checkState(targetType.getTypeParameters().length == 0,
                    "Parameterized types unsupported: %s",
                    targetType.getSimpleName());
            // 接口最多只能再实现了一个父接口，超过了不行
            Util.checkState(targetType.getInterfaces().length <= 1,
                    "Only single inheritance supported: %s",
                    targetType.getSimpleName());
            final Map<String, MethodMetadata> result = new LinkedHashMap<>();

            // 用的是getMethods（只拿当前接口自己声明的方法），而不是getDeclaredMethods（自己 + 父接口 + Object的public方法）
            for (final Method method : targetType.getMethods()) {
                /**
                 * 排除特定的方法：
                 * 1、static方法。
                 * 2、default方法。
                 * 3、带有FeignIgnore注解得方法。
                 */
                if (method.getDeclaringClass() == Object.class
                        || (method.getModifiers() & Modifier.STATIC) != 0
                        || Util.isDefault(method)
                        || method.isAnnotationPresent(FeignIgnore.class)) {
                    continue;
                }
                final MethodMetadata metadata = parseAndValidateMetadata(targetType, method);
                /**
                 * 处理重复configKey，主要和Java泛型、接口继承、桥接方法有关系。
                 */
                if (result.containsKey(metadata.configKey())) {
                    MethodMetadata existingMetadata = result.get(metadata.configKey());
                    Type existingReturnType = existingMetadata.returnType();
                    Type overridingReturnType = metadata.returnType();
                    Type resolvedType = Types.resolveReturnType(existingReturnType, overridingReturnType);
                    if (resolvedType.equals(overridingReturnType))
                        result.put(metadata.configKey(), metadata);
                    continue;
                }
                result.put(metadata.configKey(), metadata);
            }
            return new ArrayList<>(result.values());
        }

        @Deprecated
        public MethodMetadata parseAndValidateMetadata(Method method) {
            return parseAndValidateMetadata(method.getDeclaringClass(), method);
        }

        /**
         * 生成单个MethodMetadata。
         */
        protected MethodMetadata parseAndValidateMetadata(Class<?> targetType, Method method) {
            /**
             * 初始化MethodMetadata
             */
            final MethodMetadata data = new MethodMetadata();
            data.targetType(targetType);    // 保存当前Feigh接口类型
            data.method(method);    // 设置method
            data.returnType(Types.resolve(targetType, targetType, method.getGenericReturnType()));  // 解析返回类型
            data.configKey(Feign.configKey(targetType, method));    // 设置configKey，类似UserApi#getUser(Long)
            // 某些Contract子类要求：无论参数是否被识别为普通的body参数，都始终走body encode模式
            if (AlwaysEncodeBodyContract.class.isAssignableFrom(this.getClass()))
                data.alwaysEncodeBody(true);

            /**
             * 处理类级注解，会处理两次：
             * 1、如果当前接口还有父接口，先处理父接口上面的类级注解。
             * 2、处理当前接口的类级注解。
             */
            if (targetType.getInterfaces().length == 1)
                processAnnotationOnClass(data, targetType.getInterfaces()[0]);
            processAnnotationOnClass(data, targetType);

            /**
             * 处理方法级别注解。
             */
            for (final Annotation methodAnnotation : method.getAnnotations())
                processAnnotationOnMethod(data, methodAnnotation, method);

            /**
             * 有些子类实现，可能会在处理注解时发现这个方法应该被忽略，这时可以没有method字段值。
             */
            if (data.isIgnored())
                return data;

            /**
             * 校验必须存在HTTP method，因为普通feign方法必须能解析出HTTP方法类型，否则说明方法不是一个有效的Feign HTTP方法。
             */
            Util.checkState(
                    data.template().method() != null,
                    "Method %s not annotated with HTTP method type (ex. GET, POST)%s",
                    data.configKey(),
                    data.warnings());
            /**
             * 开始处理参数
             */
            final Class<?>[] parameterTypes = method.getParameterTypes();   // 参数原始Class类型
            final Type[] genericParameterTypes = method.getGenericParameterTypes(); // 参数泛型Type类型

            final Annotation[][] parameterAnnotations = method.getParameterAnnotations();   // 每个参数上的注解数组
            final int count = parameterAnnotations.length;

            // 逐个处理参数
            for (int i = 0; i < count; i++) {
                boolean isHttpAnnotation = false;
                // 识别Feign或者其它HTTP请求相关的注解
                if (parameterAnnotations[i] != null)
                    isHttpAnnotation = processAnnotationsOnParameter(data, parameterAnnotations[i], i);

                // 如果上一步解析，确实是HTTP相关注解，则标记（这个名称很有误导性，实际意思是：这个参数已被注解处理过，不要再把它当成普通body参数处理）
                if (isHttpAnnotation)
                    data.ignoreParamater(i);

                // 为了兼容Kotlin某个的编译特殊性，不管它
                if ("kotlin.coroutines.Continuation".equals(parameterTypes[i].getName()))
                    data.ignoreParamater(i);

                // 如果参数是URI，则记录成动态URL参数
                if (parameterTypes[i] == URI.class) {
                    data.urlIndex(i);
                    /**
                     * 判断是否要作为body参数：
                     * 1、没有HTTP注解。
                     * 2、不是Request.Options。
                     * 3、不是URI。
                     * 大概率就是body参数。
                     */
                } else if (!isHttpAnnotation && !Request.Options.class.isAssignableFrom(parameterTypes[i])) {
                    // 开始当作body字段来处理
                    if (data.isAlreadyProcessed(i)) {   // 已经处理过了
                        Util.checkState(
                                data.formParams().isEmpty() || data.bodyIndex() == null,
                                "Body parameters cannot be used with form parameters.%s",
                                data.warnings());
                    } else if (!data.alwaysEncodeBody()) {  // 一个方法只能有一个body类型参数
                        Util.checkState(
                                data.formParams().isEmpty(),
                                "Body parameters cannot be used with form parameters.%s",
                                data.warnings());
                        Util.checkState(
                                data.bodyIndex() == null,
                                "Method has too many Body parameters: %s%s",
                                method,
                                data.warnings());
                        data.bodyIndex(i);  // 记录哪个下标的参数是body
                        data.bodyType(Types.resolve(targetType, targetType, genericParameterTypes[i])); // 记录body参数的泛型类型
                    }
                }
            }

            // 参数循环结束，还要做2个Map参数校验
            if (data.headerMapIndex() != null) {    // 如果方法参数里有HeaderMap，则要校验key应该是String类型
                // check header map parameter for map type
                if (Map.class.isAssignableFrom(parameterTypes[data.headerMapIndex()]))
                    checkMapKeys("HeaderMap", genericParameterTypes[data.headerMapIndex()]);
            }

            if (data.queryMapIndex() != null) { // 如果方法参数里有QueryMap，则要校验key也应该是String类型
                if (Map.class.isAssignableFrom(parameterTypes[data.queryMapIndex()]))
                    checkMapKeys("QueryMap", genericParameterTypes[data.queryMapIndex()]);
            }

            return data;
        }

        private static void checkMapString(String name, Class<?> type, Type genericType) {
            Util.checkState(Map.class.isAssignableFrom(type), "%s parameter must be a Map: %s", name, type);
            checkMapKeys(name, genericType);
        }

        /**
         * 尝试从泛型类型里，拿到Map的key类型
         */
        private static void checkMapKeys(String name, Type genericType) {
            Class<?> keyClass = null;

            // 如果是Map<String, Object>这样，就可以拿
            if (ParameterizedType.class.isAssignableFrom(genericType.getClass())) {
                final Type[] parameterTypes = ((ParameterizedType) genericType).getActualTypeArguments();
                keyClass = (Class<?>) parameterTypes[0];
            } else if (genericType instanceof Class<?>) {
                // raw class, type parameters cannot be inferred directly, but we can scan any extended
                // interfaces looking for any explict types
                final Type[] interfaces = ((Class<?>) genericType).getGenericInterfaces();
                for (final Type extended : interfaces) {
                    if (ParameterizedType.class.isAssignableFrom(extended.getClass())) {
                        // use the first extended interface we find.
                        final Type[] parameterTypes = ((ParameterizedType) extended).getActualTypeArguments();
                        keyClass = (Class<?>) parameterTypes[0];
                        break;
                    }
                }
            }

            // 如果key的class不是String.class，就直接报错
            if (keyClass != null) {
                Util.checkState(
                        String.class.equals(keyClass),
                        "%s key must be a String: %s",
                        name,
                        keyClass.getSimpleName());
            }
        }

        protected abstract void processAnnotationOnClass(MethodMetadata data, Class<?> clz);

        protected abstract void processAnnotationOnMethod(MethodMetadata data, Annotation annotation, Method method);

        protected abstract boolean processAnnotationsOnParameter(MethodMetadata data, Annotation[] annotations, int paramIndex);

        /**
         * 给子类调用的方法，这个映射后面会用于：方法参数数组argv[0] -> 填充到模板变量{id}
         */
        protected void nameParam(MethodMetadata data, String name, int i) {
            final Collection<String> names = data.indexToName().containsKey(i) ? data.indexToName().get(i) : new ArrayList<>();
            names.add(name);
            data.indexToName().put(i, names);
        }
    }

    @Deprecated
    class Default extends DefaultContract {}
}
