package com.github.liyibo1110.feign;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.nonNull;

/**
 * 从guava复制而来的通用工具类。
 * @author liyibo
 * @date 2026-04-23 10:11
 */
public final class Util {

    private Util() {}

    public static final String CONTENT_LENGTH = "Content-Length";

    public static final String CONTENT_ENCODING = "Content-Encoding";

    public static final String ACCEPT_ENCODING = "Accept-Encoding";

    public static final String RETRY_AFTER = "Retry-After";

    public static final String ENCODING_GZIP = "gzip";

    public static final String ENCODING_DEFLATE = "deflate";

    public static final Charset UTF_8 = StandardCharsets.UTF_8;

    public static final Charset ISO_8859_1 = StandardCharsets.ISO_8859_1;

    private static final int BUF_SIZE = 0x800; // 2K chars (4K bytes)

    public static final Type MAP_STRING_WILDCARD =
            new Types.ParameterizedTypeImpl(
                    null,
                    Map.class,
                    String.class,
                    new Types.WildcardTypeImpl(new Type[] {Object.class}, new Type[0]));

    /**
     * 出自com.google.common.base.Preconditions#checkArgument
     */
    public static void checkArgument(boolean expression, String errorMessageTemplate, Object... errorMessageArgs) {
        if (!expression)
            throw new IllegalArgumentException(format(errorMessageTemplate, errorMessageArgs));
    }

    /**
     * 出自com.google.common.base.Preconditions#checkNotNull
     */
    public static <T> T checkNotNull(T reference, String errorMessageTemplate, Object... errorMessageArgs) {
        if (reference == null) {
            // If either of these parameters is null, the right thing happens anyway
            throw new NullPointerException(format(errorMessageTemplate, errorMessageArgs));
        }
        return reference;
    }

    /**
     * 出自com.google.common.base.Preconditions#checkState
     */
    public static void checkState(boolean expression, String errorMessageTemplate, Object... errorMessageArgs) {
        if (!expression)
            throw new IllegalStateException(format(errorMessageTemplate, errorMessageArgs));
    }

    /**
     * 检测给定Mehthod是否为接口里的default方法。
     */
    public static boolean isDefault(Method method) {
        // Default methods are public non-abstract, non-synthetic, and non-static instance methods
        // declared in an interface.
        // method.isDefault() is not sufficient for our usage as it does not check
        // for synthetic methods. As a result, it picks up overridden methods as well as actual default
        // methods.
        final int SYNTHETIC = 0x00001000;
        return ((method.getModifiers()
                & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC | SYNTHETIC))
                == Modifier.PUBLIC)
                && method.getDeclaringClass().isInterface();
    }

    /**
     * 出自com.google.common.base.Strings#emptyToNull
     */
    public static String emptyToNull(String string) {
        return string == null || string.isEmpty() ? null : string;
    }

    /**
     * 根据提供的Predicate，从数组中移除符合移除条件的元素。
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] removeValues(T[] values, Predicate<T> shouldRemove, Class<T> type) {
        Collection<T> collection = new ArrayList<>(values.length);
        for (T value : values) {
            if (shouldRemove.negate().test(value))
                collection.add(value);
        }
        T[] array = (T[]) Array.newInstance(type, collection.size());
        return collection.toArray(array);
    }

    /**
     * 出自guava的toArray
     */
    public static <T> T[] toArray(Iterable<? extends T> iterable, Class<T> type) {
        Collection<T> collection;
        if (iterable instanceof Collection) {
            collection = (Collection<T>) iterable;
        } else {
            collection = new ArrayList<>();
            for (T element : iterable)
                collection.add(element);
        }
        T[] array = (T[]) Array.newInstance(type, collection.size());
        return collection.toArray(array);
    }

    /**
     * 返回一个不可修改的集合，该集合可能为空，但绝不会为空。
     */
    public static <T> Collection<T> valuesOrEmpty(Map<String, Collection<T>> map, String key) {
        Collection<T> values = map.get(key);
        return values != null ? values : Collections.emptyList();
    }

    public static void ensureClosed(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {

            }
        }
    }

    @Deprecated
    public static Type resolveLastTypeParameter(Type genericContext, Class<?> supertype) throws IllegalStateException {
        return Types.resolveLastTypeParameter(genericContext, supertype);
    }

    /**
     * 该方法为常见的Java类型返回已知的空值。对于未列在以下列表中的类型，该方法返回null。
     * 1、[Bb]oolean
     * 2、byte[]
     * 3、Collection
     * 4、Iterator
     * 5、List
     * 6、Map
     * 7、Set
     * 在解析HTTP 404状态码时，您需要为解码器指定某种类型的默认空值。
     * 该方法仅通过查看原始类型（而非类型层次结构）来经济高效地支持常见类型。如需更精细的处理，请进行扩展。
     */
    public static Object emptyValueOf(Type type) {
        return EMPTIES.getOrDefault(Types.getRawType(type), () -> null).get();
    }

    private static final Map<Class<?>, Supplier<Object>> EMPTIES;

    static {
        final Map<Class<?>, Supplier<Object>> empties = new LinkedHashMap<>();
        empties.put(boolean.class, () -> false);
        empties.put(Boolean.class, () -> false);
        empties.put(byte[].class, () -> new byte[0]);
        empties.put(Collection.class, Collections::emptyList);
        empties.put(Iterator.class, Collections::emptyIterator);
        empties.put(List.class, Collections::emptyList);
        empties.put(Map.class, Collections::emptyMap);
        empties.put(Set.class, Collections::emptySet);
        empties.put(Optional.class, Optional::empty);
        empties.put(Stream.class, Stream::empty);
        EMPTIES = Collections.unmodifiableMap(empties);
    }

    /**
     * 出自com.google.common.io.CharStreams.toString()
     */
    public static String toString(Reader reader) throws IOException {
        if (reader == null)
            return null;

        try {
            StringBuilder to = new StringBuilder();
            CharBuffer charBuf = CharBuffer.allocate(BUF_SIZE);
            // must cast to super class Buffer otherwise break when running with java 11
            Buffer buf = charBuf;
            while (reader.read(charBuf) != -1) {
                buf.flip();
                to.append(charBuf);
                buf.clear();
            }
            return to.toString();
        } finally {
            ensureClosed(reader);
        }
    }

    /**
     * 出自com.google.common.io.ByteStreams.toByteArray()
     */
    public static byte[] toByteArray(InputStream in) throws IOException {
        checkNotNull(in, "in");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out);
            return out.toByteArray();
        } finally {
            ensureClosed(in);
        }
    }

    /**
     * 出自com.google.common.io.ByteStreams.copy()
     */
    private static long copy(InputStream from, OutputStream to) throws IOException {
        checkNotNull(from, "from");
        checkNotNull(to, "to");
        byte[] buf = new byte[BUF_SIZE];
        long total = 0;
        while (true) {
            int r = from.read(buf);
            if (r == -1)
                break;
            to.write(buf, 0, r);
            total += r;
        }
        return total;
    }

    public static String decodeOrDefault(byte[] data, Charset charset, String defaultValue) {
        if (data == null)
            return defaultValue;
        checkNotNull(charset, "charset");
        try {
            return charset.newDecoder().decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException ex) {
            return defaultValue;
        }
    }

    public static boolean isNotBlank(String value) {
        return value != null && !value.isEmpty();
    }

    public static boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }

    /**
     * 复制字符串集合的整个Map，返回一个不可修改的Map。
     */
    public static Map<String, Collection<String>> caseInsensitiveCopyOf(Map<String, Collection<String>> map) {
        if (map == null)
            return Collections.emptyMap();

        Map<String, Collection<String>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Map.Entry<String, Collection<String>> entry : map.entrySet()) {
            String key = entry.getKey();
            if (!result.containsKey(key))
                result.put(key.toLowerCase(Locale.ROOT), new LinkedList<>());
            result.get(key).addAll(entry.getValue());
        }
        result.replaceAll((key, value) -> Collections.unmodifiableCollection(value));

        return Collections.unmodifiableMap(result);
    }

    /**
     * 根据给定的class，获取里面某个Enum值。
     */
    public static <T extends Enum<?>> T enumForName(Class<T> enumClass, Object object) {
        String name = (nonNull(object)) ? object.toString() : null;
        for (T enumItem : enumClass.getEnumConstants()) {
            if (enumItem.name().equalsIgnoreCase(name) || enumItem.toString().equalsIgnoreCase(name))
                return enumItem;
        }
        return null;
    }

    /**
     * 根据给定的class，返回所有的Field。
     */
    public static List<Field> allFields(Class<?> clazz) {
        if (Objects.equals(clazz, Object.class))
            return Collections.emptyList();

        List<Field> fields = new ArrayList<>();
        fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
        fields.addAll(allFields(clazz.getSuperclass()));
        return fields;
    }

    /**
     * 获取当前线程的标识内容。
     * @return group name + thread name + thread id
     */
    public static String getThreadIdentifier() {
        Thread currentThread = Thread.currentThread();
        return currentThread.getThreadGroup()
                + "_"
                + currentThread.getName()
                + "_"
                + currentThread.getId();
    }
}
