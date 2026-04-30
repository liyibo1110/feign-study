package com.github.liyibo1110.feign.template;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RFC 6570中定义的模板表达式的通用表示形式，其中放宽了部分规则，以便该概念可在URI以外的领域中使用。
 *
 * 职责：把一个包含参数变量的字符串模板，解析成字面量片段 + 表达式片段，然后在运行时根据变量Map展开成最终的字符串。
 * 例如：/users/{id}?name={name}，会被解析成：
 * 1、Literal("/users/")
 * 2、Expression("{id}")
 * 3、Literal("?name=")
 * 4、Expression("{name}")
 * 最后调用expand(Map)时，例如：Map.of("id", 100, "name", "tom")，展开成/users/100?name=tom
 * 官方注释也说了规则有所放宽，所以除了用在URI之外，也可以用在header和body等场景（即Query、Header、Body等Template子组件）。
 *
 * @author liyibo
 * @date 2026-04-29 13:34
 */
public class Template {
    private static final Logger logger = Logger.getLogger(Template.class.getName());

    private static final Pattern QUERY_STRING_PATTERN = Pattern.compile("(?<!\\{)(\\?)");

    /** 最初的模板文本 */
    private final String template;

    /** 如果模板里有变量，但实际的Map里没有对应的值，要不要保留这个表达式 */
    private final boolean allowUnresolved;

    /** 展开值或字面量是否需要URI编码，如果是REQUIRED，空格或中文都会被编码 */
    private final EncodingOptions encode;

    /** 斜杠字符是否也要编码 */
    private final boolean encodeSlash;
    private final Charset charset;

    /** 解析后的模板片段，Template在构造时，就会把模板拆成一组TemplateChunk，即Literal和Expression两类 */
    private final List<TemplateChunk> templateChunks = new ArrayList<>();

    Template(String value,
             ExpansionOptions allowUnresolved,
             EncodingOptions encode,
             boolean encodeSlash,
             Charset charset) {
        if (value == null)
            throw new IllegalArgumentException("template is required.");

        this.template = value;
        this.allowUnresolved = ExpansionOptions.ALLOW_UNRESOLVED == allowUnresolved;
        this.encode = encode;
        this.encodeSlash = encodeSlash;
        this.charset = charset;
        this.parseTemplate();
    }

    Template(ExpansionOptions allowUnresolved,
             EncodingOptions encode,
             boolean encodeSlash,
             Charset charset,
             List<TemplateChunk> chunks) {
        this.templateChunks.addAll(chunks);
        this.allowUnresolved = ExpansionOptions.ALLOW_UNRESOLVED == allowUnresolved;
        this.encode = encode;
        this.encodeSlash = encodeSlash;
        this.charset = charset;
        this.template = this.toString();
    }

    public String expand(Map<String, ?> variables) {
        if (variables == null)
            throw new IllegalArgumentException("variable map is required.");

        /* resolve all expressions within the template */
        StringBuilder resolved = null;
        for (TemplateChunk chunk : this.templateChunks) {
            String expanded;
            if (chunk instanceof Expression) {
                expanded = this.resolveExpression((Expression) chunk, variables);
            } else {
                /* chunk is a literal value */
                expanded = chunk.getValue();
            }
            if (expanded == null)
                continue;

            /* append it to the result */
            if (resolved == null)
                resolved = new StringBuilder();

            resolved.append(expanded);
        }

        if (resolved == null) {
            /* entire template is unresolved */
            return null;
        }

        return resolved.toString();
    }

    protected String resolveExpression(Expression expression, Map<String, ?> variables) {
        String resolved = null;
        Object value = variables.get(expression.getName());
        if (value != null) {
            String expanded = expression.expand(value, this.encode.isEncodingRequired());
            if (expanded != null) {
                if (!this.encodeSlash) {
                    logger.fine("Explicit slash decoding specified, decoding all slashes in uri");
                    expanded = expanded.replaceAll("%2F", "/");
                }
                resolved = expanded;
            }
        } else {
            if (this.allowUnresolved) {
                /* unresolved variables are treated as literals */
                resolved = encodeLiteral(expression.toString());
            }
        }
        return resolved;
    }

    private String encodeLiteral(String value) {
        return this.encodeLiteral() ? UriUtils.encode(value, this.charset, true) : value;
    }

    public List<String> getVariables() {
        return this.templateChunks.stream()
                .filter(templateChunk -> Expression.class.isAssignableFrom(templateChunk.getClass()))
                .map(templateChunk -> ((Expression) templateChunk).getName())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<String> getLiterals() {
        return this.templateChunks.stream()
                .filter(templateChunk -> Literal.class.isAssignableFrom(templateChunk.getClass()))
                .map(TemplateChunk::toString)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<TemplateChunk> getTemplateChunks() {
        return Collections.unmodifiableList(this.templateChunks);
    }

    public boolean isLiteral() {
        return this.getVariables().isEmpty();
    }

    private void parseTemplate() {

        /* parse the entire template */
        this.parseFragment(this.template);
    }

    private void parseFragment(String fragment) {
        ChunkTokenizer tokenizer = new ChunkTokenizer(fragment);

        while (tokenizer.hasNext()) {
            /* check to see if we have an expression or a literal */
            String chunk = tokenizer.next();

            if (chunk.startsWith("{")) {
                Expression expression = Expressions.create(chunk);
                if (expression == null)
                    this.templateChunks.add(Literal.create(this.encodeLiteral(chunk)));
                else
                    this.templateChunks.add(expression);
            } else {
                this.templateChunks.add(Literal.create(this.encodeLiteral(chunk)));
            }
        }
    }

    @Override
    public String toString() {
        return this.templateChunks.stream().map(TemplateChunk::getValue).collect(Collectors.joining());
    }

    public boolean encodeLiteral() {
        return encode.isEncodingRequired();
    }

    boolean encodeSlash() {
        return encodeSlash;
    }

    public Charset getCharset() {
        return this.charset;
    }

    /**
     * 把原始字符串按照{}边界，拆成token，例如：/users/{id}/orders/{orderId}
     * 1、/users/
     * 2、{id}
     * 3、/orders/
     * 4、{orderId}
     */
    static class ChunkTokenizer {

        private List<String> tokens = new ArrayList<>();
        private int index;

        ChunkTokenizer(String template) {
            boolean outside = true;
            int level = 0;
            int lastIndex = 0;
            int idx;

            /* loop through the template, character by character */
            for (idx = 0; idx < template.length(); idx++) {
                if (template.charAt(idx) == '{') {
                    /* start of an expression */
                    if (outside) {
                        /* outside of an expression */
                        if (lastIndex < idx) {
                            /* this is the start of a new token */
                            tokens.add(template.substring(lastIndex, idx));
                        }
                        lastIndex = idx;

                        /*
                         * no longer outside of an expression, additional characters will be treated as in an
                         * expression
                         */
                        outside = false;
                    } else {
                        /* nested braces, increase our nesting level */
                        level++;
                    }
                } else if (template.charAt(idx) == '}' && !outside) {
                    /* the end of an expression */
                    if (level > 0) {
                        /*
                         * sometimes we see nested expressions, we only want the outer most expression
                         * boundaries.
                         */
                        level--;
                    } else {
                        /* outermost boundary */
                        if (lastIndex < idx) {
                            /* this is the end of an expression token */
                            tokens.add(template.substring(lastIndex, idx + 1));
                        }
                        lastIndex = idx + 1;

                        /* outside an expression */
                        outside = true;
                    }
                }
            }
            if (lastIndex < idx) {
                /* grab the remaining chunk */
                tokens.add(template.substring(lastIndex, idx));
            }
        }

        public boolean hasNext() {
            return this.tokens.size() > this.index;
        }

        public String next() {
            if (hasNext()) {
                return this.tokens.get(this.index++);
            }
            throw new IllegalStateException("No More Elements");
        }
    }

    public enum EncodingOptions {
        REQUIRED(true),
        NOT_REQUIRED(false);

        private final boolean shouldEncode;

        EncodingOptions(boolean shouldEncode) {
            this.shouldEncode = shouldEncode;
        }

        public boolean isEncodingRequired() {
            return this.shouldEncode;
        }
    }

    public enum ExpansionOptions {
        ALLOW_UNRESOLVED,
        REQUIRED
    }
}
