package com.github.liyibo1110.feign.querymap;

import com.github.liyibo1110.feign.Param;
import com.github.liyibo1110.feign.QueryMapEncoder;
import com.github.liyibo1110.feign.codec.EncodeException;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * QueryMapEncoder的实现类之一，对应JavaBean getter方式，即从getter方法中提取字段信息。
 * @author liyibo
 * @date 2026-04-29 10:18
 */
public class BeanQueryMapEncoder implements QueryMapEncoder {

    /**
     * 是个cache，存放：class -> 这个类有哪些可读的JavaBean属性，在第一次解析这个类的时候就把字段列表缓存下来。
     */
    private final Map<Class<?>, ObjectParamMetadata> classToMetadata = new HashMap<>();

    @Override
    public Map<String, Object> encode(Object object) throws EncodeException {
        if (object == null)
            return Collections.emptyMap();
        try {
            // 获取这个类的元数据（保存的是List<PropertyDescriptor>，也就是JavaBean属性集合）
            ObjectParamMetadata metadata = getMetadata(object.getClass());
            Map<String, Object> propertyNameToValue = new HashMap<>();
            for (PropertyDescriptor pd : metadata.objectProperties) {
                Method method = pd.getReadMethod();
                Object value = method.invoke(object);
                if (value != null && value != object) {
                    // 额外处理getter方法上带有@Param注解的情况，这时key要用Param里面的值
                    Param alias = method.getAnnotation(Param.class);
                    String name = alias != null ? alias.value() : pd.getName();
                    propertyNameToValue.put(name, value);
                }
            }
            return propertyNameToValue;
        } catch (IllegalAccessException | IntrospectionException | InvocationTargetException e) {
            throw new EncodeException("Failure encoding object into query map", e);
        }
    }

    private ObjectParamMetadata getMetadata(Class<?> objectType) throws IntrospectionException {
        // 先查cache
        ObjectParamMetadata metadata = classToMetadata.get(objectType);
        if (metadata == null) {
            // cache未命中，则扫描整个类
            metadata = ObjectParamMetadata.parseObjectType(objectType);
            classToMetadata.put(objectType, metadata);
        }
        return metadata;
    }

    private static class ObjectParamMetadata {
        private final List<PropertyDescriptor> objectProperties;

        private ObjectParamMetadata(List<PropertyDescriptor> objectProperties) {
            this.objectProperties = Collections.unmodifiableList(objectProperties);
        }

        /**
         * class -> ObjectParamMetadata（里面有所有JavaBean属性）
         */
        private static ObjectParamMetadata parseObjectType(Class<?> type) throws IntrospectionException {
            List<PropertyDescriptor> properties = new ArrayList<>();
            // Introspector这里面的方法操作比较耗时，所以外部用了cache
            for (PropertyDescriptor pd : Introspector.getBeanInfo(type).getPropertyDescriptors()) {
                // 排除getClass这个通用方法
                boolean isGetterMethod = pd.getReadMethod() != null && !"class".equals(pd.getName());
                if (isGetterMethod)
                    properties.add(pd);
            }

            return new ObjectParamMetadata(properties);
        }
    }
}
