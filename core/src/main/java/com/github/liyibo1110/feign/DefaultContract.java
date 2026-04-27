package com.github.liyibo1110.feign;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DeclarativeContract的实现类，因为DeclarativeContract已经锁死了3个process方法，所以这个实现类只负责构造处理器并且注册。
 * @author liyibo
 * @date 2026-04-27 15:12
 */
public class DefaultContract extends DeclarativeContract {
    static final Pattern REQUEST_LINE_PATTERN = Pattern.compile("^([A-Z]+)[ ]*(.*)$");

    public DefaultContract() {
        super.registerClassAnnotation(
                Headers.class,
                (header, data) -> {
                    final String[] headersOnType = header.value();
                    Util.checkState(headersOnType.length > 0, "Headers annotation was empty on type %s.", data.configKey());
                    final Map<String, Collection<String>> headers = toMap(headersOnType);
                    headers.putAll(data.template().headers());
                    data.template().headers(null); // to clear
                    data.template().headers(headers);
                });
        super.registerMethodAnnotation(
                RequestLine.class,
                (ann, data) -> {
                    final String requestLine = ann.value();
                    Util.checkState(Util.emptyToNull(requestLine) != null, "RequestLine annotation was empty on method %s.", data.configKey());

                    final Matcher requestLineMatcher = REQUEST_LINE_PATTERN.matcher(requestLine);
                    if (!requestLineMatcher.find()) {
                        throw new IllegalStateException(String.format("RequestLine annotation didn't start with an HTTP verb on method %s", data.configKey()));
                    } else {
                        data.template().method(Request.HttpMethod.valueOf(requestLineMatcher.group(1)));
                        data.template().uri(requestLineMatcher.group(2));
                    }
                    data.template().decodeSlash(ann.decodeSlash());
                    data.template().collectionFormat(ann.collectionFormat());
                });
        super.registerMethodAnnotation(
                Body.class,
                (ann, data) -> {
                    final String body = ann.value();
                    Util.checkState(Util.emptyToNull(body) != null, "Body annotation was empty on method %s.", data.configKey());
                    if (body.indexOf('{') == -1)
                        data.template().body(body);
                    else
                        data.template().bodyTemplate(body);
                });
        super.registerMethodAnnotation(
                Headers.class,
                (header, data) -> {
                    final String[] headersOnMethod = header.value();
                    Util.checkState(headersOnMethod.length > 0, "Headers annotation was empty on method %s.", data.configKey());
                    data.template().headers(toMap(headersOnMethod));
                });
        super.registerParameterAnnotation(
                Param.class,
                (paramAnnotation, data, paramIndex) -> {
                    final String annotationName = paramAnnotation.value();
                    final Parameter parameter = data.method().getParameters()[paramIndex];
                    final String name;
                    if (Util.emptyToNull(annotationName) == null && parameter.isNamePresent())
                        name = parameter.getName();
                    else
                        name = annotationName;

                    Util.checkState(Util.emptyToNull(name) != null,
                            "Param annotation was empty on param %s.\nHint: %s",
                            paramIndex,
                            "Prefer using @Param(value=\"name\"), or compile your code with the -parameters flag.\n"
                                    + "If the value is missing, Feign attempts to retrieve the parameter name from bytecode, "
                                    + "which only works if the class was compiled with the -parameters flag.");
                    nameParam(data, name, paramIndex);
                    final Class<? extends Param.Expander> expander = paramAnnotation.expander();
                    if (expander != Param.ToStringExpander.class)
                        data.indexToExpanderClass().put(paramIndex, expander);
                    if (!data.template().hasRequestVariable(name))
                        data.formParams().add(name);
                });
        super.registerParameterAnnotation(
                QueryMap.class,
                (queryMap, data, paramIndex) -> {
                    Util.checkState(data.queryMapIndex() == null, "QueryMap annotation was present on multiple parameters.");
                    data.queryMapIndex(paramIndex);
                    data.queryMapEncoder(queryMap.mapEncoder().instance());
                });
        super.registerParameterAnnotation(
                HeaderMap.class,
                (queryMap, data, paramIndex) -> {
                    Util.checkState(data.headerMapIndex() == null, "HeaderMap annotation was present on multiple parameters.");
                    data.headerMapIndex(paramIndex);
                });
    }


    private static Map<String, Collection<String>> toMap(String[] input) {
        final Map<String, Collection<String>> result = new LinkedHashMap<>(input.length);
        for (final String header : input) {
            final int colon = header.indexOf(':');
            final String name = header.substring(0, colon);
            if (!result.containsKey(name))
                result.put(name, new ArrayList<>(1));
            result.get(name).add(header.substring(colon + 1).trim());
        }
        return result;
    }
}
