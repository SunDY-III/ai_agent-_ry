package com.zhishu.knowledge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义切分 + 滑动窗口重叠。
 *
 * 策略（面试点）：
 * 1. 先按自然段（空行/换行）聚合 —— 尊重语义边界，优于无脑定长切；
 * 2. 段落聚合到接近 chunkSize 就封块；单段超长再退化为定长硬切；
 * 3. 相邻块保留 overlap 个字符重叠 —— 防止关键句恰好被切断在块边界，
 *    检索时无论命中哪一块都能拿到完整语义。
 * 取舍：chunk 太小丢上下文，太大稀释相关性（一块里混入大量无关内容拉低相似度）。
 */
@Component
public class TextSplitter {

    @Value("${app.rag.chunk-size}")    private int chunkSize;
    @Value("${app.rag.chunk-overlap}") private int overlap;

    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        String[] paragraphs = text.split("\\n\\s*\\n|\\r\\n\\s*\\r\\n");
        StringBuilder buf = new StringBuilder();

        for (String p : paragraphs) {
            String para = p.strip();
            if (para.isEmpty()) continue;

            if (para.length() > chunkSize) {           // 单段超长：先封当前块，再对该段定长硬切
                flush(chunks, buf);
                hardSplit(chunks, para);
            } else if (buf.length() + para.length() > chunkSize) {
                flush(chunks, buf);
                buf.append(tailOverlap(chunks)).append(para).append('\n');
            } else {
                buf.append(para).append('\n');
            }
        }
        flush(chunks, buf);
        return chunks;
    }

    private void hardSplit(List<String> chunks, String para) {
        int step = chunkSize - overlap;
        for (int start = 0; start < para.length(); start += step) {
            chunks.add(para.substring(start, Math.min(start + chunkSize, para.length())));
            if (start + chunkSize >= para.length()) break;
        }
    }

    /** 取上一块尾部 overlap 字符拼到新块头部 */
    private String tailOverlap(List<String> chunks) {
        if (chunks.isEmpty() || overlap <= 0) return "";
        String prev = chunks.get(chunks.size() - 1);
        return prev.length() <= overlap ? prev : prev.substring(prev.length() - overlap);
    }

    private void flush(List<String> chunks, StringBuilder buf) {
        if (buf.length() > 0) {
            chunks.add(buf.toString().strip());
            buf.setLength(0);
        }
    }
}
