package com.zhishu.governance;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * DFA（确定性有限自动机）敏感词过滤：词库构建成 Trie 树，
 * 对文本单次线性扫描即可完成多模式匹配，复杂度 O(n)，
 * 优于逐词 contains 的 O(n*m)。输入（用户提问）与输出（模型回答）双向拦截。
 */
@Slf4j
@Component
public class SensitiveWordFilter {

    private final TrieNode root = new TrieNode();

    static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean end = false;
    }

    @PostConstruct
    public void load() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("sensitive-words.txt").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                String word = line.strip();
                if (!word.isEmpty()) { addWord(word); count++; }
            }
            log.info("sensitive words loaded: {}", count);
        } catch (Exception e) {
            log.warn("sensitive words load failed", e);
        }
    }

    private void addWord(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        node.end = true;
    }

    public boolean containsSensitive(String text) {
        return scan(text, false) != null;
    }

    /** 输出侧：命中词替换为 *** */
    public String replaceSensitive(String text) {
        String result = scan(text, true);
        return result == null ? text : result;
    }

    /** replace=false 时发现命中立即返回非 null 标记；replace=true 时返回替换后的全文 */
    private String scan(String text, boolean replace) {
        if (text == null || text.isEmpty()) return null;
        StringBuilder sb = replace ? new StringBuilder(text) : null;
        boolean hit = false;
        for (int i = 0; i < text.length(); i++) {
            TrieNode node = root;
            int j = i;
            int matchedEnd = -1;
            while (j < text.length() && node.children.containsKey(text.charAt(j))) {
                node = node.children.get(text.charAt(j));
                j++;
                if (node.end) matchedEnd = j;   // 贪婪：记录最长匹配
            }
            if (matchedEnd > 0) {
                hit = true;
                if (!replace) return "";        // 仅检测：发现即返回
                for (int k = i; k < matchedEnd; k++) sb.setCharAt(k, '*');
                i = matchedEnd - 1;
            }
        }
        return hit ? (replace ? sb.toString() : "") : null;
    }
}
