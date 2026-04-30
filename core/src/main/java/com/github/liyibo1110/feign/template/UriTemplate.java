package com.github.liyibo1110.feign.template;

import java.nio.charset.Charset;

/**
 * @author liyibo
 * @date 2026-04-29 14:22
 */
public class UriTemplate extends Template {

    public static UriTemplate create(String template, Charset charset) {
        return new UriTemplate(template, true, charset);
    }

    public static UriTemplate create(String template, boolean encodeSlash, Charset charset) {
        return new UriTemplate(template, encodeSlash, charset);
    }

    public static UriTemplate append(UriTemplate uriTemplate, String fragment) {
        return new UriTemplate(uriTemplate.toString() + fragment, uriTemplate.encodeSlash(), uriTemplate.getCharset());
    }

    private UriTemplate(String template, boolean encodeSlash, Charset charset) {
        super(template, ExpansionOptions.REQUIRED, EncodingOptions.REQUIRED, encodeSlash, charset);
    }
}
