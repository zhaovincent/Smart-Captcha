package cn.localtool.smartcaptcha.spring.autoconfigure;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 仅当 {@code smart-captcha.store.type} 归一化后等于指定值时生效。
 *
 * <p>相比 {@code @ConditionalOnProperty}，这里支持 {@code redis-template} / {@code lettuce}
 * 等别名，且未知值会落到 {@code memory}（与内存实现的行为保持一致）。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnStoreTypeCondition.class)
public @interface ConditionalOnStoreType {

    /** memory / redis / redisson。 */
    String value();
}
