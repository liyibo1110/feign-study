package com.github.liyibo1110.feign.template;

import com.github.liyibo1110.feign.Util;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * 适用于带有@feign.Body注解的模板的Template。
 * 未解析的表达式将作为字面量保留，且字面量不会进行URI编码。
 * @author liyibo
 * @date 2026-04-29 14:16
 */
public class BodyTemplate extends Template {

    private static final String JSON_TOKEN_START = "{";
    private static final String JSON_TOKEN_END = "}";
    private static final String JSON_TOKEN_START_ENCODED = "%7B";
    private static final String JSON_TOKEN_END_ENCODED = "%7D";
    private boolean json = false;

    public static BodyTemplate create(String template) {
        return new BodyTemplate(template, Util.UTF_8);
    }

    public static BodyTemplate create(String template, Charset charset) {
        return new BodyTemplate(template, charset);
    }

    private BodyTemplate(String value, Charset charset) {
        super(value, ExpansionOptions.ALLOW_UNRESOLVED, EncodingOptions.NOT_REQUIRED, false, charset);
        if (value.startsWith(JSON_TOKEN_START_ENCODED) && value.endsWith(JSON_TOKEN_END_ENCODED))
            this.json = true;
    }

    @Override
    public String expand(Map<String, ?> variables) {
        String expanded = super.expand(variables);
        if (this.json) {
            /* restore all start and end tokens */
            expanded = expanded.replaceAll(JSON_TOKEN_START_ENCODED, JSON_TOKEN_START);
            expanded = expanded.replaceAll(JSON_TOKEN_END_ENCODED, JSON_TOKEN_END);
        }
        return expanded;
    }
}
