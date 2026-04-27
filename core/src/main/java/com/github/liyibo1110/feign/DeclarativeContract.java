package com.github.liyibo1110.feign;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 一种基于契约的实现方式，其工作原理是声明应处理哪些注解，以及每个注解如何修改MethodMetadata。
 *
 * 职责：定义了注册注解处理器的机制，也就是提供了把具体注解和具体处理逻辑注册怎么到一起的功能。
 * 注意Contract接口的3个级别process方法，在这里就已经是final的了，说明处理流程已经在此固定，子类只能通过注册processor来实现功能扩展。
 * @author liyibo
 * @date 2026-04-27 14:08
 */
public abstract class DeclarativeContract extends Contract.BaseContract {

    /**
     * 类级注解通常用于设置：
     * 1、公共header
     * 2、公共path前缀
     * 3、公共produces / consumes
     * 用List是因为：既支持用注解精确匹配，还可以按Predicate匹配。
     */
    private final List<GuardedAnnotationProcessor> classAnnotationProcessors = new ArrayList<>();

    /**
     * 方法级别注解处理器，例如：
     * @RequestLine("GET /users/{id}")
     * User getUser(Long id);
     * 或者：
     * @GetMapping("/users/{id}")
     * User getUser(Long id);
     *
     * 通常用于设置：
     * 1、HTTP method
     * 2、URL path
     * 3、headers
     * 4、content-type
     * 5、query模板
     * 用List是因为：既支持用注解精确匹配，还可以按Predicate匹配。
     */
    private final List<GuardedAnnotationProcessor> methodAnnotationProcessors = new ArrayList<>();

    /**
     * 参数级别注解处理器，例如：
     * 1、@Param("id")
     * 2、@PathVariable("id")
     * 3、@RequestParam("name")
     * 4、@HeaderMap
     * 5、@QueryMap
     * 用Map是因为：只支持用注解精确匹配。
     */
    private final Map<Class<Annotation>, DeclarativeContract.ParameterAnnotationProcessor<Annotation>> parameterAnnotationProcessors = new HashMap<>();

    @Override
    public final List<MethodMetadata> parseAndValidateMetadata(Class<?> targetType) {
        // any implementations must register processors
        return super.parseAndValidateMetadata(targetType);
    }

    @Override
    protected final void processAnnotationOnClass(MethodMetadata data, Class<?> targetType) {
        // 用Predicate匹配
        final List<GuardedAnnotationProcessor> processors =
                Arrays.stream(targetType.getAnnotations())
                        .flatMap(annotation -> classAnnotationProcessors.stream().filter(processor -> processor.test(annotation)))
                        .collect(Collectors.toList());

        // 如果上面找到了processor，再次遍历注解并执行process方法（也就是把注解的信息写入MethodMetadata）
        if (!processors.isEmpty()) {
            Arrays.stream(targetType.getAnnotations()).forEach(annotation -> processors.stream()
                                            .filter(processor -> processor.test(annotation))
                                            .forEach(processor -> processor.process(annotation, data)));
        } else { // 没有找到processor，则添加warning，因为允许类上面没有注解，所以这里只记录信息
            if (targetType.getAnnotations().length == 0) {
                data.addWarning(String.format("Class %s has no annotations, it may affect contract %s", targetType.getSimpleName(), getClass().getSimpleName()));
            } else {
                data.addWarning(String.format("Class %s has annotations %s that are not used by contract %s",
                                targetType.getSimpleName(),
                                Arrays.stream(targetType.getAnnotations())
                                        .map(annotation -> annotation.annotationType().getSimpleName())
                                        .collect(Collectors.toList()),
                                getClass().getSimpleName()));
            }
        }
    }

    @Override
    protected final void processAnnotationOnMethod(MethodMetadata data, Annotation annotation, Method method) {
        // 用Predicate匹配
        List<GuardedAnnotationProcessor> processors =
                methodAnnotationProcessors.stream().filter(processor -> processor.test(annotation)).collect(Collectors.toList());

        // 找到处理器，则开始process，否则记录warning
        if (!processors.isEmpty()) {
            processors.forEach(processor -> processor.process(annotation, data));
        } else {
            data.addWarning(String.format("Method %s has an annotation %s that is not used by contract %s",
                            method.getName(),
                            annotation.annotationType().getSimpleName(),
                            getClass().getSimpleName()));
        }
    }

    @Override
    protected final boolean processAnnotationsOnParameter(MethodMetadata data, Annotation[] annotations, int paramIndex) {
        // 找出当前参数上，可以被处理的注解
        List<Annotation> matchingAnnotations = Arrays.stream(annotations)
                                                     .filter(annotation -> parameterAnnotationProcessors.containsKey(annotation.annotationType()))
                                                     .collect(Collectors.toList());
        // 找到了就逐个处理
        if (!matchingAnnotations.isEmpty()) {
            matchingAnnotations.forEach(
                    annotation -> parameterAnnotationProcessors
                                    .getOrDefault(annotation.annotationType(), ParameterAnnotationProcessor.DO_NOTHING)
                                    .process(annotation, data, paramIndex));

        } else {    // 找不到就记录warning，因为就算参数没有注解，可能也会是body参数
            final Parameter parameter = data.method().getParameters()[paramIndex];
            String parameterName = parameter.isNamePresent() ? parameter.getName() : parameter.getType().getSimpleName();
            if (annotations.length == 0) {
                data.addWarning(String.format("Parameter %s has no annotations, it may affect contract %s", parameterName, getClass().getSimpleName()));
            } else {
                data.addWarning(
                        String.format("Parameter %s has annotations %s that are not used by contract %s",
                                parameterName,
                                Arrays.stream(annotations)
                                        .map(annotation -> annotation.annotationType().getSimpleName())
                                        .collect(Collectors.toList()),
                                getClass().getSimpleName()));
            }
        }
        return false;   // 注意这个方法总是会返回false，因为判断是否被HTTP注解处理，是通过MethodMetadata对象里面的字段来看，貌似有历史包袱
    }

    /**
     * 向classAnnotationProcessors注册。
     */
    protected <E extends Annotation> void registerClassAnnotation(Class<E> annotationType, DeclarativeContract.AnnotationProcessor<E> processor) {
        registerClassAnnotation(annotation -> annotation.annotationType().equals(annotationType), processor);
    }

    /**
     * 向classAnnotationProcessors注册。
     */
    protected <E extends Annotation> void registerClassAnnotation(Predicate<E> predicate, DeclarativeContract.AnnotationProcessor<E> processor) {
        this.classAnnotationProcessors.add(new GuardedAnnotationProcessor(predicate, processor));
    }

    /**
     * 向methodAnnotationProcessors注册。
     */
    protected <E extends Annotation> void registerMethodAnnotation(Class<E> annotationType, DeclarativeContract.AnnotationProcessor<E> processor) {
        registerMethodAnnotation(annotation -> annotation.annotationType().equals(annotationType), processor);
    }

    /**
     * 向methodAnnotationProcessors注册。
     */
    protected <E extends Annotation> void registerMethodAnnotation(Predicate<E> predicate, DeclarativeContract.AnnotationProcessor<E> processor) {
        this.methodAnnotationProcessors.add(new GuardedAnnotationProcessor(predicate, processor));
    }

    /**
     * 向parameterAnnotationProcessors注册。
     */
    protected <E extends Annotation> void registerParameterAnnotation(Class<E> annotation, DeclarativeContract.ParameterAnnotationProcessor<E> processor) {
        this.parameterAnnotationProcessors.put((Class) annotation, (DeclarativeContract.ParameterAnnotationProcessor) processor);
    }

    @FunctionalInterface
    public interface AnnotationProcessor<E extends Annotation> {

        /**
         * 根据传入的注解，处理特定MethodMetadata。
         */
        void process(E annotation, MethodMetadata metadata);
    }

    @FunctionalInterface
    public interface ParameterAnnotationProcessor<E extends Annotation> {
        DeclarativeContract.ParameterAnnotationProcessor<Annotation> DO_NOTHING = (ann, data, i) -> {};

        /**
         * 根据传入的注解，处理特定MethodMetadata的特定下标参数。
         */
        void process(E annotation, MethodMetadata metadata, int paramIndex);
    }

    private class GuardedAnnotationProcessor implements Predicate<Annotation>, DeclarativeContract.AnnotationProcessor<Annotation> {
        private final Predicate<Annotation> predicate;
        private final DeclarativeContract.AnnotationProcessor<Annotation> processor;

        private GuardedAnnotationProcessor(Predicate predicate, DeclarativeContract.AnnotationProcessor processor) {
            this.predicate = predicate;
            this.processor = processor;
        }

        @Override
        public void process(Annotation annotation, MethodMetadata metadata) {
            processor.process(annotation, metadata);
        }

        @Override
        public boolean test(Annotation t) {
            return predicate.test(t);
        }
    }
}
