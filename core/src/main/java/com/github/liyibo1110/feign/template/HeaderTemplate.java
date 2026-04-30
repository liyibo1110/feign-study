package com.github.liyibo1110.feign.template;

import com.github.liyibo1110.feign.Util;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 专门处理header部分的Template组件。
 * @author liyibo
 * @date 2026-04-29 14:14
 */
public final class HeaderTemplate {

    private final String name;
    private final List<Template> values = new CopyOnWriteArrayList<>();

    public static HeaderTemplate create(String name, Iterable<String> values) {
        if (name == null || name.isEmpty())
            throw new IllegalArgumentException("name is required.");

        if (values == null)
            throw new IllegalArgumentException("values are required");

        return new HeaderTemplate(name, values, Util.UTF_8);
    }

    public static HeaderTemplate literal(String name, Iterable<String> values) {
        if (name == null || name.isEmpty())
            throw new IllegalArgumentException("name is required.");

        if (values == null)
            throw new IllegalArgumentException("values are required");

        return new HeaderTemplate(name, values, Util.UTF_8, true);
    }

    public static HeaderTemplate append(HeaderTemplate headerTemplate, Iterable<String> values) {
        LinkedHashSet<String> headerValues = new LinkedHashSet<>(headerTemplate.getValues());
        headerValues.addAll(StreamSupport.stream(values.spliterator(), false)
                        .filter(Util::isNotBlank)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
        return create(headerTemplate.getName(), headerValues);
    }

    public static HeaderTemplate appendLiteral(HeaderTemplate headerTemplate, Iterable<String> values) {
        LinkedHashSet<String> headerValues = new LinkedHashSet<>(headerTemplate.getValues());
        headerValues.addAll(StreamSupport.stream(values.spliterator(), false)
                        .filter(Util::isNotBlank)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
        return literal(headerTemplate.getName(), headerValues);
    }

    private HeaderTemplate(String name, Iterable<String> values, Charset charset) {
        this(name, values, charset, false);
    }

    private HeaderTemplate(String name, Iterable<String> values, Charset charset, boolean literal) {
        this.name = name;

        for (String value : values) {
            if (value == null || value.isEmpty()) {
                /* skip */
                continue;
            }

            if (literal) {
                this.values.add(new Template(
                                Template.ExpansionOptions.ALLOW_UNRESOLVED,
                                Template.EncodingOptions.NOT_REQUIRED,
                                false,
                                charset,
                                Collections.singletonList(Literal.create(value))));
            } else {
                this.values.add(new Template(value, Template.ExpansionOptions.REQUIRED, Template.EncodingOptions.NOT_REQUIRED, false, charset));
            }
        }
    }

    public Collection<String> getValues() {
        return Collections.unmodifiableList(this.values.stream().map(Template::toString).collect(Collectors.toList()));
    }

    public List<String> getVariables() {
        List<String> variables = new ArrayList<>();
        for (Template template : this.values)
            variables.addAll(template.getVariables());

        return Collections.unmodifiableList(variables);
    }

    public String getName() {
        return this.name;
    }

    public String expand(Map<String, ?> variables) {
        List<String> expanded = new ArrayList<>();
        if (!this.values.isEmpty()) {
            for (Template template : this.values) {
                String result = template.expand(variables);

                if (result == null) {
                    /* ignore unresolved values */
                    continue;
                }

                expanded.add(result);
            }
        }

        StringBuilder result = new StringBuilder();
        if (!expanded.isEmpty())
            result.append(String.join(", ", expanded));

        return result.toString();
    }
}
