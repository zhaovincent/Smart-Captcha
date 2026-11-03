package cn.localtool.smartcaptcha.core.generator;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 中文字符池规模：现代汉语常用字前 2000 字，且无重复（重复字会导致提示与答案错乱概率上升）。
 */
class ClickPoolTests {

    @Test
    void cjkPoolHas2000UniqueCharacters() throws Exception {
        Field field = ClickWordCaptchaGenerator.class.getDeclaredField("CJK_POOL");
        field.setAccessible(true);
        String[] pool = (String[]) field.get(null);

        Set<String> all = new HashSet<>();
        for (String chunk : pool) {
            for (int i = 0; i < chunk.length(); i++) {
                String ch = chunk.substring(i, i + 1);
                assertTrue(ch.codePointAt(0) >= 0x4E00 && ch.codePointAt(0) <= 0x9FFF,
                        "非汉字字符: " + ch);
                all.add(ch);
            }
        }
        assertEquals(2000, all.size(), "字符池应为 2000 个不重复汉字");
    }
}
