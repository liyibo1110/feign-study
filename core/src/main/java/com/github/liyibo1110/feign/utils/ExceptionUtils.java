package com.github.liyibo1110.feign.utils;

import java.util.HashSet;
import java.util.Set;

/**
 * @author liyibo
 * @date 2026-04-23 10:06
 */
public final class ExceptionUtils {

    private ExceptionUtils() {}

    /**
     * 检查Throwable对象以获取根本原因。
     * 该方法通过Throwable.getCause()遍历异常链直至树的最后一个元素（即“根”），并返回该异常。
     */
    public static Throwable getRootCause(Throwable t) {
        if (t == null)
            return null;
        Throwable rootCause = t;
        // 避免cause嵌套导致的无限循环
        final Set<Throwable> seenThrowables = new HashSet<>();
        seenThrowables.add(rootCause);
        while ((rootCause.getCause() != null && !seenThrowables.contains(rootCause.getCause()))) {
            seenThrowables.add(rootCause.getCause());
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
}
