package com.aidoc.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切片器 —— 本项目的检索质量主要由它决定。
 *
 * <p><b>为什么不能简单按固定长度切？</b>
 * 固定长度切分会把句子拦腰截断（例如把「系统支持 PDF 和 | TXT 两种格式」切成两片），
 * 两片各自的语义都不完整，向量化后与问题的相似度双双下降，导致该召回的内容召回不到。
 * 因此本实现采用<b>语义边界优先 + 定长兜底</b>的策略。</p>
 *
 * <p><b>切分算法</b>：
 * <ol>
 *   <li>从当前位置向后取 chunkSize 个字符作为目标切点；</li>
 *   <li>在 {@code [起点 + 半长, 目标切点]} 窗口内<b>从后往前</b>寻找语义边界，命中即在此处切分
 *       （从后往前 = 尽量让切片更长、更完整）；</li>
 *   <li>边界按优先级逐级回退：段落分隔 → 换行 → 句末标点 → 子句标点 → 空白；</li>
 *   <li>所有边界都没命中（如超长无标点串）才硬切，保证不会漏掉内容；</li>
 *   <li>下一片起点回退 chunkOverlap 个字符形成<b>重叠窗口</b>，避免语义刚好跨在切点上时两边都丢。</li>
 * </ol></p>
 *
 * <p>窗口下限取 {@code 起点 + 半长}，是为了避免在切片开头附近就切断而产生大量碎片。</p>
 */
@Component
public class TextChunker {

    /**
     * 硬边界：段落与句末标点。按长度降序排列，
     * 保证同一位置优先匹配更长的边界（如 "\r\n\r\n" 优先于 "\n\n" 优先于 "\n"）。
     */
    private static final String[] HARD_BOUNDARIES = {
            "\r\n\r\n", "\n\n", ". ", "! ", "? ", "; ", "\n", "。", "！", "？", "；", ".", "!", "?"
    };

    /** 软边界：子句标点与空白，硬边界找不到时才回退到这里 */
    private static final String[] SOFT_BOUNDARIES = {
            "，", "、", ",", "：", ":", " ", "\t"
    };

    /**
     * 把整篇文档切分为若干语义切片。
     *
     * @param text         文档全文
     * @param chunkSize    目标切片字符数
     * @param overlap      相邻切片重叠字符数
     * @return 切片列表（按原文档顺序）；文本为空时返回空列表
     */
    public List<TextChunk> split(String text, int chunkSize, int overlap) {
        List<TextChunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String normalized = normalize(text);
        int length = normalized.length();
        int size = Math.max(1, chunkSize);
        // 重叠不得超过切片长度的一半，否则游标可能不前进（死循环）
        int step = Math.max(0, Math.min(overlap, size / 2));

        int start = 0;
        int index = 0;
        while (start < length) {
            int target = Math.min(start + size, length);
            int end = target;
            if (target < length) {
                // 窗口下限：至少切出半个切片，避免产生过碎片段
                int minEnd = start + size / 2;
                end = findBoundary(normalized, minEnd, target, HARD_BOUNDARIES);
                if (end <= start) {
                    end = findBoundary(normalized, minEnd, target, SOFT_BOUNDARIES);
                }
                if (end <= start) {
                    // 兜底：整段没有任何可用边界（如超长无标点串），只能硬切
                    end = target;
                }
            }

            String content = normalized.substring(start, end).trim();
            if (!content.isEmpty()) {
                TextChunk chunk = new TextChunk();
                chunk.setIndex(index++);
                chunk.setContent(content);
                chunk.setCharStart(start);
                chunk.setCharEnd(end);
                chunks.add(chunk);
            }

            if (end >= length) {
                break;
            }
            // 下一片起点回退 step 个字符形成重叠；并保证严格前进，杜绝死循环
            int next = end - step;
            start = next > start ? next : end;
        }
        return chunks;
    }

    /**
     * 在 {@code [from, to)} 窗口内从后往前寻找语义边界。
     *
     * <p>从后往前扫描，命中的第一个边界即最靠后的边界，切片因此尽可能长。</p>
     *
     * @param text       规范化文本
     * @param from       窗口起点（含）
     * @param to         窗口终点（不含），同时限制边界不得越界
     * @param boundaries 边界串数组（按优先级排序）
     * @return 切分位置（边界之后的下标）；未命中返回 -1
     */
    private int findBoundary(String text, int from, int to, String[] boundaries) {
        for (int pos = to - 1; pos >= from; pos--) {
            for (String boundary : boundaries) {
                int len = boundary.length();
                // 边界必须完整落在窗口内
                if (pos + len <= to && text.regionMatches(pos, boundary, 0, len)) {
                    return pos + len;
                }
            }
        }
        return -1;
    }

    /**
     * 文本规范化：统一换行符、压缩连续空白。
     *
     * <p>切片记录的下标均相对本方法产出的<b>规范化文本</b>，而非原始解析文本。</p>
     *
     * @param text 原始文本
     * @return 规范化文本
     */
    private String normalize(String text) {
        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                // 3 个以上连续换行压缩为 2 个（保留段落感，去掉排版留白）
                .replaceAll("\n{3,}", "\n\n")
                // 行内连续空格/制表符压缩为单个空格
                .replaceAll("[ \\t\\x0B\\f]{2,}", " ")
                .trim();
    }
}
