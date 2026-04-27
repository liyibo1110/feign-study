package com.github.liyibo1110.feign.template;

import com.github.liyibo1110.feign.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author liyibo
 * @date 2026-04-27 15:19
 */
public class UriUtils {

    private static final Pattern PCT_ENCODED_PATTERN = Pattern.compile("%[0-9A-Fa-f][0-9A-Fa-f]");

    /**
     * 判断value是否已进行百分比编码。
     */
    public static boolean isEncoded(String value, Charset charset) {
        for (byte b : value.getBytes(charset)) {
            if (!isUnreserved((char) b) && b != '%') {
                /* break if there are any unreserved character */
                return false;
            }
        }
        return PCT_ENCODED_PATTERN.matcher(value).find();
    }

    /**
     * 使用默认字符集对值进行URI编码，已编码的值将被跳过。
     */
    public static String encode(String value) {
        return encodeChunk(value, Util.UTF_8, false);
    }

    public static String encode(String value, Charset charset) {
        return encodeChunk(value, charset, false);
    }

    public static String encode(String value, boolean allowReservedCharacters) {
        return encodeInternal(value, Util.UTF_8, allowReservedCharacters);
    }

    public static String encode(String value, Charset charset, boolean allowReservedCharacters) {
        return encodeInternal(value, charset, allowReservedCharacters);
    }

    /**
     * 对值进行URI解码。
     */
    public static String decode(String value, Charset charset) {
        try {
            /* there is nothing special between uri and url decoding */
            return URLDecoder.decode(value, charset.name());
        } catch (UnsupportedEncodingException uee) {
            /* since the encoding is not supported, return the original value */
            return value;
        }
    }

    /**
     * 判断给定的URI是否为绝对URI。
     */
    public static boolean isAbsolute(String uri) {
        return uri != null && !uri.isEmpty() && uri.startsWith("http");
    }

    /**
     * 对值进行编码，同时保留所有保留字符。
     * 已进行pct编码的值将被忽略。
     */
    public static String encodeInternal(String value, Charset charset, boolean allowReservedCharacters) {
        /* value is encoded, we need to split it up and skip the parts that are already encoded */
        Matcher matcher = PCT_ENCODED_PATTERN.matcher(value);

        if (!matcher.find())
            return encodeChunk(value, charset, true);

        int length = value.length();
        StringBuilder encoded = new StringBuilder(length + 8);
        int index = 0;
        do {
            /* split out the value before the encoded value */
            String before = value.substring(index, matcher.start());

            /* encode it */
            encoded.append(encodeChunk(before, charset, allowReservedCharacters));

            /* append the encoded value */
            encoded.append(matcher.group());

            /* update the string search index */
            index = matcher.end();
        } while (matcher.find());

        /* append the rest of the string */
        String tail = value.substring(index, length);
        encoded.append(encodeChunk(tail, charset, allowReservedCharacters));
        return encoded.toString();
    }

    /**
     * 对URI片段进行编码，确保所有保留字符也一并编码。
     */
    private static String encodeChunk(String value, Charset charset, boolean allowReserved) {
        if (isEncoded(value, charset))
            return value;

        byte[] data = value.getBytes(charset);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            for (byte b : data) {
                if (isUnreserved((char) b))
                    bos.write(b);
                else if (isReserved((char) b) && allowReserved)
                    bos.write(b);
                else
                    pctEncode(b, bos);
            }
            return new String(bos.toByteArray(), charset);
        } catch (IOException ioe) {
            throw new IllegalStateException("Error occurred during encoding of the uri: " + ioe.getMessage(), ioe);
        }
    }

    /**
     * 对给定的字节进行Percent Encode编码。
     */
    private static void pctEncode(byte data, ByteArrayOutputStream bos) {
        bos.write('%');
        char hex1 = Character.toUpperCase(Character.forDigit((data >> 4) & 0xF, 16));
        char hex2 = Character.toUpperCase(Character.forDigit(data & 0xF, 16));
        bos.write(hex1);
        bos.write(hex2);
    }

    private static boolean isAlpha(int c) {
        return (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z');
    }

    private static boolean isDigit(int c) {
        return (c >= '0' && c <= '9');
    }

    private static boolean isGenericDelimiter(int c) {
        return (c == ':')
                || (c == '/')
                || (c == '?')
                || (c == '#')
                || (c == '[')
                || (c == ']')
                || (c == '@');
    }

    private static boolean isSubDelimiter(int c) {
        return (c == '!')
                || (c == '$')
                || (c == '&')
                || (c == '\'')
                || (c == '(')
                || (c == ')')
                || (c == '*')
                || (c == '+')
                || (c == ',')
                || (c == ';')
                || (c == '=');
    }

    private static boolean isUnreserved(int c) {
        return isAlpha(c) || isDigit(c) || c == '-' || c == '.' || c == '_' || c == '~';
    }

    private static boolean isReserved(int c) {
        return isGenericDelimiter(c) || isSubDelimiter(c);
    }

    private boolean isPchar(int c) {
        return isUnreserved(c) || isSubDelimiter(c) || c == ':' || c == '@';
    }
}
