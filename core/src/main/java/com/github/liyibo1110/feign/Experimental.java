package com.github.liyibo1110.feign;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 表示某个公共API（公共类、方法或字段）在未来版本中可能会发生不兼容的变更，甚至被移除。
 * 带有此注解的API不受其所属库所作的任何兼容性保证的约束。
 * 请注意，此注解的存在并不意味着该API的质量或性能存在任何问题，仅表示其API尚未“冻结”。
 *
 * 应用程序依赖beta API通常是安全的，但升级时需要额外的工作。
 * 然而对于库（这些库会被包含在用户的CLASSPATH中，且超出库开发者的控制范围）而言，通常不建议这样做。
 *
 * 灵感来源于guava的@Beta。
 * @author liyibo
 * @date 2026-04-26 10:51
 */
@Retention(RetentionPolicy.CLASS)
@Target({
    ElementType.ANNOTATION_TYPE,
    ElementType.CONSTRUCTOR,
    ElementType.FIELD,
    ElementType.METHOD,
    ElementType.TYPE
})
@Documented
public @interface Experimental {
}
