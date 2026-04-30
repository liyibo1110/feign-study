package com.github.liyibo1110.feign.template;

/**
 * 表示URI模板的各个部分。
 * @author liyibo
 * @date 2026-04-29 11:53
 */
@FunctionalInterface
public interface TemplateChunk {

    String getValue();
}
