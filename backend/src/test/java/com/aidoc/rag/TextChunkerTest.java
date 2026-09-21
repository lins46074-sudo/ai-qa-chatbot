package com.aidoc.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文本切片器单元测试。
 *
 * <p>切片是检索质量的地基：切片有空洞会丢内容，切片过长会稀释语义，
 * 切断句子会让两侧片段都变得不完整、双双召回失败。因此这里针对这三点分别断言。</p>
 */
class TextChunkerTest {

    private final TextChunker chunker = new TextChunker();

    /**
     * 构造一篇由等长句子组成的测试文档（句子足够短，保证任意窗口内都能找到句末标点）。
     *
     * @param sentenceCount 句子数量
     * @return 文档全文
     */
    private String buildDocument(int sentenceCount) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < sentenceCount; i++) {
            builder.append("这是第").append(i).append("个用于测试切片的句子，它需要足够长以便触发切片逻辑。");
        }
        return builder.toString();
    }

    @Test
    @DisplayName("切片应连续覆盖全文，不得出现空洞")
    void shouldCoverWholeTextWithoutGap() {
        List<TextChunk> chunks = chunker.split(buildDocument(40), 120, 20);

        assertFalse(chunks.isEmpty(), "长文档应切出多个片段");
        assertTrue(chunks.size() > 1, "40 句文档应切出多于 1 个片段");

        // 逐片校验下标连续：后一片的起点不得晚于前一片的终点（否则中间内容丢失）
        int cursor = 0;
        for (TextChunk chunk : chunks) {
            assertTrue(chunk.getCharStart() <= cursor,
                    "切片出现空洞：第 " + chunk.getIndex() + " 片起点 " + chunk.getCharStart()
                            + " 晚于前一片终点 " + cursor);
            cursor = Math.max(cursor, chunk.getCharEnd());
        }
    }

    @Test
    @DisplayName("切片长度不得超过目标长度，且序号应从 0 连续递增")
    void shouldRespectChunkSizeAndIndex() {
        int chunkSize = 120;
        List<TextChunk> chunks = chunker.split(buildDocument(40), chunkSize, 20);

        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            assertEquals(i, chunk.getIndex(), "切片序号应从 0 连续递增");
            assertTrue(chunk.getContent().length() <= chunkSize,
                    "第 " + i + " 片长度 " + chunk.getContent().length() + " 超过上限 " + chunkSize);
        }
    }

    @Test
    @DisplayName("相邻切片之间应有重叠，避免语义恰好跨在切点上时两侧都丢")
    void shouldOverlapBetweenAdjacentChunks() {
        List<TextChunk> chunks = chunker.split(buildDocument(40), 120, 20);

        boolean hasOverlap = false;
        for (int i = 1; i < chunks.size(); i++) {
            if (chunks.get(i).getCharStart() < chunks.get(i - 1).getCharEnd()) {
                hasOverlap = true;
                break;
            }
        }
        assertTrue(hasOverlap, "相邻切片之间应存在重叠区间");
    }

    @Test
    @DisplayName("切片不得把句子拦腰截断（除最后一片外应以句末标点收尾）")
    void shouldNotBreakSentences() {
        List<TextChunk> chunks = chunker.split(buildDocument(40), 120, 20);

        // 除最后一片外，每片都应在语义边界处结束
        for (int i = 0; i < chunks.size() - 1; i++) {
            String content = chunks.get(i).getContent();
            char tail = content.charAt(content.length() - 1);
            assertTrue("。！？；\n".indexOf(tail) >= 0,
                    "第 " + i + " 片未在语义边界结束，结尾为：「"
                            + content.substring(Math.max(0, content.length() - 15)) + "」");
        }
    }

    @Test
    @DisplayName("空文本与空白文本应返回空列表，而非抛异常")
    void shouldHandleEmptyText() {
        assertTrue(chunker.split(null, 100, 20).isEmpty());
        assertTrue(chunker.split("", 100, 20).isEmpty());
        assertTrue(chunker.split("   \n\t  ", 100, 20).isEmpty());
    }

    @Test
    @DisplayName("短于切片长度的文档应整体作为一个切片")
    void shouldKeepShortTextAsSingleChunk() {
        String text = "这是一段很短的文档内容。";
        List<TextChunk> chunks = chunker.split(text, 500, 80);

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0).getContent());
    }

    @Test
    @DisplayName("重叠长度大于切片长度时不得死循环（游标必须前进）")
    void shouldTerminateWhenOverlapExceedsChunkSize() {
        List<TextChunk> chunks = chunker.split(buildDocument(20), 100, 500);
        assertFalse(chunks.isEmpty(), "极端重叠配置下仍应正常切分而非死循环");
    }
}
