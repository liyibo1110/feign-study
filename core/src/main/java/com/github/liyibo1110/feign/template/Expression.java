package com.github.liyibo1110.feign.template;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * URI模板参数变量（对应Literal字面量组件）
 * @author liyibo
 * @date 2026-04-29 14:18
 */
abstract class Expression implements TemplateChunk {

    private String name;
    private Pattern pattern;

    Expression(String name, String pattern) {
        this.name = name;
        Optional.ofNullable(pattern).ifPresent(s -> this.pattern = Pattern.compile(s));
    }

    abstract String expand(Object variable, boolean encode);

    public String getName() {
        return this.name;
    }

    Pattern getPattern() {
        return pattern;
    }

    boolean matches(String value) {
        if (pattern == null)
            return true;

        return pattern.matcher(value).matches();
    }

    @Override
    public String getValue() {
        if (this.pattern != null)
            return "{" + this.name + ":" + this.pattern + "}";

        return "{" + this.name + "}";
    }

    @Override
    public String toString() {
        return this.getValue();
    }
}
