package cn.localtool.smartcaptcha.spring.autoconfigure;

import cn.localtool.smartcaptcha.spring.store.StoreTypes;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

/** {@link ConditionalOnStoreType} 的实现。 */
class OnStoreTypeCondition extends SpringBootCondition {

    private static final String PROPERTY = "smart-captcha.store.type";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnStoreType.class.getName());
        String expected = attributes == null ? StoreTypes.MEMORY : String.valueOf(attributes.get("value"));
        String actual = StoreTypes.normalize(context.getEnvironment().getProperty(PROPERTY));

        ConditionMessage.Builder message = ConditionMessage.forCondition(ConditionalOnStoreType.class);
        if (expected.equalsIgnoreCase(actual)) {
            return ConditionOutcome.match(message.foundExactly(PROPERTY + "=" + actual));
        }
        return ConditionOutcome.noMatch(message.because(PROPERTY + " is " + actual + ", not " + expected));
    }
}
