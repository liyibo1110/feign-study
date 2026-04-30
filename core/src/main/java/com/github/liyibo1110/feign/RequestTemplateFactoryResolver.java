package com.github.liyibo1110.feign;

import com.github.liyibo1110.feign.codec.EncodeException;
import com.github.liyibo1110.feign.codec.Encoder;
import com.github.liyibo1110.feign.template.UriUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 重要组件，根据MethodMetadata的不同形态，选择一种RequestTemplate.Factory，
 * 在每次接口方法调用时，把argv实参数组转换成本次请求的RequestTemplate，即方法元数据 + 本次调用实参 -> 本次的RequestTemplate
 * @author liyibo
 * @date 2026-04-29 11:27
 */
final class RequestTemplateFactoryResolver {

    /** 用于处理request body或form参数，最终写入RequestTemplate里 */
    private final Encoder encoder;

    /** 用于处理@QueryMap或@HeaderMap这类对象转Map的参数 */
    private final QueryMapEncoder queryMapEncoder;

    RequestTemplateFactoryResolver(Encoder encoder, QueryMapEncoder queryMapEncoder) {
        this.encoder = Util.checkNotNull(encoder, "encoder");
        this.queryMapEncoder = Util.checkNotNull(queryMapEncoder, "queryMapEncoder");
    }

    /**
     * 选择三种Factory之一
     */
    public RequestTemplate.Factory resolve(Target<?> target, MethodMetadata md) {
        if (!md.formParams().isEmpty() && md.template().bodyTemplate() == null)
            return new BuildFormEncodedTemplateFromArgs(md, encoder, queryMapEncoder, target);
        else if (md.bodyIndex() != null || md.alwaysEncodeBody())
            return new BuildEncodedTemplateFromArgs(md, encoder, queryMapEncoder, target);
        else
            return new BuildTemplateByResolvingArgs(md, queryMapEncoder, target);
    }

    /**
     * 用于普通模板参数的场景，即没有body和form的普通GET请求。
     */
    static class BuildTemplateByResolvingArgs implements RequestTemplate.Factory {
        private final QueryMapEncoder queryMapEncoder;

        protected final MethodMetadata metadata;
        protected final Target<?> target;
        private final Map<Integer, Param.Expander> indexToExpander = new LinkedHashMap<>();

        BuildTemplateByResolvingArgs(MethodMetadata metadata, QueryMapEncoder queryMapEncoder, Target target) {
            this.metadata = metadata;
            this.target = target;
            this.queryMapEncoder = queryMapEncoder;
            if (metadata.indexToExpander() != null) {
                indexToExpander.putAll(metadata.indexToExpander());
                return;
            }
            if (metadata.indexToExpanderClass().isEmpty())
                return;

            for (Map.Entry<Integer, Class<? extends Param.Expander>> indexToExpanderClass : metadata.indexToExpanderClass().entrySet()) {
                try {
                    indexToExpander.put(indexToExpanderClass.getKey(), indexToExpanderClass.getValue().newInstance());
                } catch (InstantiationException e) {
                    throw new IllegalStateException(e);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        @Override
        public RequestTemplate create(Object[] argv) {
            RequestTemplate mutable = RequestTemplate.from(metadata.template());
            mutable.feignTarget(target);
            if (metadata.urlIndex() != null) {
                int urlIndex = metadata.urlIndex();
                Util.checkArgument(argv[urlIndex] != null, "URI parameter %s was null", urlIndex);
                mutable.target(String.valueOf(argv[urlIndex]));
            }
            Map<String, Object> varBuilder = new LinkedHashMap<String, Object>();
            for (Map.Entry<Integer, Collection<String>> entry : metadata.indexToName().entrySet()) {
                int i = entry.getKey();
                Object value = argv[entry.getKey()];
                if (value != null) { // Null values are skipped.
                    if (indexToExpander.containsKey(i))
                        value = expandElements(indexToExpander.get(i), value);

                    for (String name : entry.getValue())
                        varBuilder.put(name, value);
                }
            }

            RequestTemplate template = resolve(argv, mutable, varBuilder);
            if (metadata.queryMapIndex() != null) {
                // add query map parameters after initial resolve so that they take
                // precedence over any predefined values
                Object value = argv[metadata.queryMapIndex()];
                Map<String, Object> queryMap = toQueryMap(value, metadata.queryMapEncoder());
                template = addQueryMapQueryParameters(queryMap, template);
            }

            if (metadata.headerMapIndex() != null) {
                // add header map parameters for a resolution of the user pojo object
                Object value = argv[metadata.headerMapIndex()];
                Map<String, Object> headerMap = toQueryMap(value, metadata.queryMapEncoder());
                template = addHeaderMapHeaders(headerMap, template);
            }

            return template;
        }

        private Map<String, Object> toQueryMap(Object value, QueryMapEncoder queryMapEncoder) {
            if (value instanceof Map)
                return (Map<String, Object>) value;

            try {
                // encode with @QueryMap annotation if exists otherwise with the one from this resolver
                return queryMapEncoder != null
                        ? queryMapEncoder.encode(value)
                        : this.queryMapEncoder.encode(value);
            } catch (EncodeException e) {
                throw new IllegalStateException(e);
            }
        }

        private Object expandElements(Param.Expander expander, Object value) {
            if (value instanceof Iterable)
                return expandIterable(expander, (Iterable) value);

            return expander.expand(value);
        }

        private List<String> expandIterable(Param.Expander expander, Iterable value) {
            List<String> values = new ArrayList<String>();
            for (Object element : value) {
                if (element != null)
                    values.add(expander.expand(element));
            }
            return values;
        }

        @SuppressWarnings("unchecked")
        private RequestTemplate addHeaderMapHeaders(Map<String, Object> headerMap, RequestTemplate mutable) {
            for (Map.Entry<String, Object> currEntry : headerMap.entrySet()) {
                Collection<String> values = new ArrayList<String>();

                Object currValue = currEntry.getValue();
                if (currValue instanceof Iterable<?>) {
                    Iterator<?> iter = ((Iterable<?>) currValue).iterator();
                    while (iter.hasNext()) {
                        Object nextObject = iter.next();
                        values.add(nextObject == null ? null : nextObject.toString());
                    }
                } else {
                    values.add(currValue == null ? null : currValue.toString());
                }

                mutable.header(currEntry.getKey(), values);
            }
            return mutable;
        }

        @SuppressWarnings("unchecked")
        private RequestTemplate addQueryMapQueryParameters(Map<String, Object> queryMap, RequestTemplate mutable) {
            for (Map.Entry<String, Object> currEntry : queryMap.entrySet()) {
                Collection<String> values = new ArrayList<String>();

                Object currValue = currEntry.getValue();
                if (currValue instanceof Iterable<?>) {
                    Iterator<?> iter = ((Iterable<?>) currValue).iterator();
                    while (iter.hasNext()) {
                        Object nextObject = iter.next();
                        values.add(nextObject == null ? null : UriUtils.encode(nextObject.toString()));
                    }
                } else if (currValue instanceof Object[]) {
                    for (Object value : (Object[]) currValue)
                        values.add(value == null ? null : UriUtils.encode(value.toString()));
                } else {
                    if (currValue != null)
                        values.add(UriUtils.encode(currValue.toString()));
                }

                if (values.size() > 0)
                    mutable.query(UriUtils.encode(currEntry.getKey()), values);
            }
            return mutable;
        }

        protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable, Map<String, Object> variables) {
            return mutable.resolve(variables);
        }
    }

    /**
     * 用于form编码的场景，即有form参数，并且没有bodyTemplate的方法。
     * 在继承基础模板处理能力后，重写resolve方法，追加form encode操作。
     */
    static class BuildFormEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {

        private final Encoder encoder;

        BuildFormEncodedTemplateFromArgs(
                MethodMetadata metadata, Encoder encoder, QueryMapEncoder queryMapEncoder, Target target) {
            super(metadata, queryMapEncoder, target);
            this.encoder = encoder;
        }

        @Override
        protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable, Map<String, Object> variables) {
            Map<String, Object> formVariables = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                if (metadata.formParams().contains(entry.getKey()))
                    formVariables.put(entry.getKey(), entry.getValue());
            }
            try {
                encoder.encode(formVariables, Encoder.MAP_STRING_WILDCARD, mutable);
            } catch (EncodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new EncodeException(e.getMessage(), e);
            }
            return super.resolve(argv, mutable, variables);
        }
    }

    /**
     * 用于body编码的场景，即有body参数的方法。
     */
    static class BuildEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {

        private final Encoder encoder;

        BuildEncodedTemplateFromArgs(
                MethodMetadata metadata, Encoder encoder, QueryMapEncoder queryMapEncoder, Target target) {
            super(metadata, queryMapEncoder, target);
            this.encoder = encoder;
        }

        @Override
        protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable, Map<String, Object> variables) {

            boolean alwaysEncodeBody = mutable.methodMetadata().alwaysEncodeBody();

            Object body = null;
            if (!alwaysEncodeBody) {
                body = argv[metadata.bodyIndex()];
                if (mutable.methodMetadata().isBodyRequired())
                    Util.checkArgument(body != null, "Body parameter %s was null", metadata.bodyIndex());
            }

            try {
                if (alwaysEncodeBody) {
                    body = argv == null ? new Object[0] : argv;
                    encoder.encode(body, Object[].class, mutable);
                } else {
                    encoder.encode(body, metadata.bodyType(), mutable);
                }
            } catch (EncodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new EncodeException(e.getMessage(), e);
            }
            return super.resolve(argv, mutable, variables);
        }
    }
}
