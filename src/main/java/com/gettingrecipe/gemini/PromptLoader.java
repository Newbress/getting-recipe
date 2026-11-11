package com.gettingrecipe.gemini;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** resources/prompts/{name}.txt 를 읽고 {{변수}} 를 치환한다. */
@Component
public class PromptLoader {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String load(String name, Map<String, String> variables) {
        String template = cache.computeIfAbsent(name, this::read);
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            if (!variables.containsKey(key)) {
                throw new IllegalStateException("프롬프트 '" + name + "'에 값이 없는 변수: " + key);
            }
            // 한 번만 치환한다 (사용자 입력 안의 {{...}} 가 다시 치환되지 않도록)
            m.appendReplacement(out, Matcher.quoteReplacement(variables.get(key)));
        }
        m.appendTail(out);
        return out.toString().strip();
    }

    private String read(String name) {
        try (InputStream in = new ClassPathResource("prompts/" + name + ".txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("프롬프트 파일을 읽을 수 없습니다: " + name, e);
        }
    }
}
