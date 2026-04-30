package com.github.liyibo1110.feign.template;

/**
 * URI模板字面量。
 * @author liyibo
 * @date 2026-04-29 11:54
 */
public class Literal implements TemplateChunk {

    private final String value;

    public static Literal create(String value) {
        return new Literal(value);
    }

    Literal(String value) {
        if (value == null || value.isEmpty())
            throw new IllegalArgumentException("a value is required.");

        this.value = value;
    }

    @Override
    public String getValue() {
        return this.value;
    }
}
