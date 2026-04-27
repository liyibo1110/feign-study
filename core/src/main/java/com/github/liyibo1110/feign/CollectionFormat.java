package com.github.liyibo1110.feign;

import com.github.liyibo1110.feign.template.UriUtils;

import java.nio.charset.Charset;
import java.util.Collection;

/**
 * @author liyibo
 * @date 2026-04-27 15:17
 */
public enum CollectionFormat {
    /** foo=bar,baz */
    CSV(","),

    /** foo=bar baz */
    SSV(" "),

    /** foo=bar[tab]baz */
    TSV("\t"),

    /** foo=bar|baz */
    PIPES("|"),

    /** foo=bar&foo=baz */
    EXPLODED(null);

    private final String separator;

    CollectionFormat(String separator) {
        this.separator = separator;
    }

    public CharSequence join(String field, Collection<String> values, Charset charset) {
        StringBuilder builder = new StringBuilder();
        int valueCount = 0;
        for (String value : values) {
            if (separator == null) {
                // exploded
                builder.append(valueCount++ == 0 ? "" : "&");
                builder.append(UriUtils.encode(field, charset));
                if (value != null) {
                    builder.append('=');
                    builder.append(value);
                }
            } else {
                // delimited with a separator character
                if (builder.length() == 0) {
                    builder.append(UriUtils.encode(field, charset));
                }
                if (value == null)
                    continue;

                builder.append(valueCount++ == 0 ? "=" : UriUtils.encode(separator, charset));
                builder.append(value);
            }
        }
        return builder;
    }
}
