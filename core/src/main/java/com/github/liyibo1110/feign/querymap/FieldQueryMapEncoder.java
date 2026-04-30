package com.github.liyibo1110.feign.querymap;

import com.github.liyibo1110.feign.Param;
import com.github.liyibo1110.feign.QueryMapEncoder;
import com.github.liyibo1110.feign.codec.EncodeException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * QueryMapEncoder的实现类之一，对应字段方式，即直接从字段属性中提取信息，不走getter方法。
 * @author liyibo
 * @date 2026-04-29 10:26
 */
public class FieldQueryMapEncoder implements QueryMapEncoder {

    /** cache */
    private final Map<Class<?>, ObjectParamMetadata> classToMetadata = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> encode(Object object) throws EncodeException {
        if (object == null)
            return Collections.emptyMap();

        // 查cache，没有则生成并写入
        ObjectParamMetadata metadata = classToMetadata.computeIfAbsent(object.getClass(), ObjectParamMetadata::parseObjectType);

        return metadata.objectFields.stream()
                .map(field -> this.FieldValuePair(object, field))
                .filter(fieldObjectPair -> fieldObjectPair.right.isPresent())   // 过滤掉null值的字段
                .collect(Collectors.toMap(this::fieldName, fieldObjectPair -> fieldObjectPair.right.get()));
    }

    private String fieldName(Pair<Field, Optional<Object>> pair) {
        Param alias = pair.left.getAnnotation(Param.class);
        return alias != null ? alias.value() : pair.left.getName();
    }

    private Pair<Field, Optional<Object>> FieldValuePair(Object object, Field field) {
        try {
            return Pair.pair(field, Optional.ofNullable(field.get(object)));
        } catch (IllegalAccessException e) {
            throw new EncodeException("Failure encoding object into query map", e);
        }
    }

    private static class ObjectParamMetadata {
        private final List<Field> objectFields;

        private ObjectParamMetadata(List<Field> objectFields) {
            this.objectFields = Collections.unmodifiableList(objectFields);
        }

        private static ObjectParamMetadata parseObjectType(Class<?> type) {
            List<Field> allFields = new ArrayList();

            for (Class currentClass = type;
                 currentClass != null;
                 currentClass = currentClass.getSuperclass()) {
                 Collections.addAll(allFields, currentClass.getDeclaredFields());
            }

            return new ObjectParamMetadata(allFields.stream()
                            .filter(field -> !field.isSynthetic())
                            .peek(field -> field.setAccessible(true))
                            .collect(Collectors.toList()));
        }
    }

    private static class Pair<T, U> {
        public final T left;
        public final U right;

        private Pair(T left, U right) {
            this.right = right;
            this.left = left;
        }

        public static <T, U> Pair<T, U> pair(T left, U right) {
            return new Pair<>(left, right);
        }
    }
}
