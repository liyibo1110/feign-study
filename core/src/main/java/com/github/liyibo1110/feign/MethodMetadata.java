package com.github.liyibo1110.feign;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Method要转成的目标对象，封装了这个方法调用的各种信息
 * @author liyibo
 * @date 2026-04-24 16:48
 */
public final class MethodMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private String configKey;
    private transient Type returnType;
    private Integer urlIndex;
    private Integer bodyIndex;
    private Integer headerMapIndex;
    private Integer queryMapIndex;
    private QueryMapEncoder queryMapEncoder;
    private boolean alwaysEncodeBody;
    private transient Type bodyType;
    private final RequestTemplate template = new RequestTemplate();
    private final List<String> formParams = new ArrayList<>();
    private final Map<Integer, Collection<String>> indexToName = new LinkedHashMap<>();
    private final Map<Integer, Class<? extends Param.Expander>> indexToExpanderClass = new LinkedHashMap<>();
    private final Map<Integer, Boolean> indexToEncoded = new LinkedHashMap<>();
    private transient Map<Integer, Param.Expander> indexToExpander;
    private BitSet parameterToIgnore = new BitSet();
    private boolean ignored;
    private boolean bodyRequired = true;
    private transient Class<?> targetType;
    private transient Method method;
    private final transient List<String> warnings = new ArrayList<>();

    MethodMetadata() {
        template.methodMetadata(this);
    }

    public String configKey() {
        return configKey;
    }

    public MethodMetadata configKey(String configKey) {
        this.configKey = configKey;
        return this;
    }

    public Type returnType() {
        return returnType;
    }

    public MethodMetadata returnType(Type returnType) {
        this.returnType = returnType;
        return this;
    }

    public Integer urlIndex() {
        return urlIndex;
    }

    public MethodMetadata urlIndex(Integer urlIndex) {
        this.urlIndex = urlIndex;
        return this;
    }

    public Integer bodyIndex() {
        return bodyIndex;
    }

    public MethodMetadata bodyIndex(Integer bodyIndex) {
        this.bodyIndex = bodyIndex;
        return this;
    }

    public Integer headerMapIndex() {
        return headerMapIndex;
    }

    public MethodMetadata headerMapIndex(Integer headerMapIndex) {
        this.headerMapIndex = headerMapIndex;
        return this;
    }

    public Integer queryMapIndex() {
        return queryMapIndex;
    }

    public MethodMetadata queryMapIndex(Integer queryMapIndex) {
        this.queryMapIndex = queryMapIndex;
        return this;
    }

    public QueryMapEncoder queryMapEncoder() {
        return queryMapEncoder;
    }

    public MethodMetadata queryMapEncoder(QueryMapEncoder queryMapEncoder) {
        this.queryMapEncoder = queryMapEncoder;
        return this;
    }

    @Experimental
    public boolean alwaysEncodeBody() {
        return alwaysEncodeBody;
    }

    @Experimental
    MethodMetadata alwaysEncodeBody(boolean alwaysEncodeBody) {
        this.alwaysEncodeBody = alwaysEncodeBody;
        return this;
    }

    public Type bodyType() {
        return bodyType;
    }

    public MethodMetadata bodyType(Type bodyType) {
        this.bodyType = bodyType;
        return this;
    }

    public RequestTemplate template() {
        return template;
    }

    public List<String> formParams() {
        return formParams;
    }

    public Map<Integer, Collection<String>> indexToName() {
        return indexToName;
    }

    public Map<Integer, Boolean> indexToEncoded() {
        return indexToEncoded;
    }

    public Map<Integer, Class<? extends Param.Expander>> indexToExpanderClass() {
        return indexToExpanderClass;
    }

    public MethodMetadata indexToExpander(Map<Integer, Param.Expander> indexToExpander) {
        this.indexToExpander = indexToExpander;
        return this;
    }

    public Map<Integer, Param.Expander> indexToExpander() {
        return indexToExpander;
    }

    public MethodMetadata ignoreParamater(int i) {
        this.parameterToIgnore.set(i);
        return this;
    }

    public BitSet parameterToIgnore() {
        return parameterToIgnore;
    }

    public MethodMetadata parameterToIgnore(BitSet parameterToIgnore) {
        this.parameterToIgnore = parameterToIgnore;
        return this;
    }

    public boolean shouldIgnoreParamater(int i) {
        return parameterToIgnore.get(i);
    }

    /**
     * 如果参数索引已被任何MethodMetadata持有者使用过，则返回true。
     */
    public boolean isAlreadyProcessed(Integer index) {
        return index.equals(urlIndex)
                || index.equals(bodyIndex)
                || index.equals(headerMapIndex)
                || index.equals(queryMapIndex)
                || indexToName.containsKey(index)
                || indexToExpanderClass.containsKey(index)
                || indexToEncoded.containsKey(index)
                || (indexToExpander != null && indexToExpander.containsKey(index))
                || parameterToIgnore.get(index);
    }

    public void ignoreMethod() {
        this.ignored = true;
    }

    public boolean isIgnored() {
        return ignored;
    }

    public boolean isBodyRequired() {
        return bodyRequired;
    }

    public void setBodyRequired(boolean bodyRequired) {
        this.bodyRequired = bodyRequired;
    }

    @Experimental
    public MethodMetadata targetType(Class<?> targetType) {
        this.targetType = targetType;
        return this;
    }

    @Experimental
    public Class<?> targetType() {
        return targetType;
    }

    @Experimental
    public MethodMetadata method(Method method) {
        this.method = method;
        return this;
    }

    @Experimental
    public Method method() {
        return method;
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public String warnings() {
        return warnings.stream().collect(Collectors.joining("\n- ", "\nWarnings:\n- ", ""));
    }
}
