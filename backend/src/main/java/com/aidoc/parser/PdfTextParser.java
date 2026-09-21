package com.aidoc.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * PDF 文本解析器（Apache PDFBox）。
 *
 * <p>逐页抽取文本并统计页数。若 PDF 无可用文本层（如纯扫描件），
 * 抽取结果为空串 —— 由上层 DocumentService 判定为解析失败并给出友好提示。</p>
 */
@Component
public class PdfTextParser {

    /** 解析结果：页数 + 全文 */
    public static class ParseResult {
        private final int pageCount;
        private final String text;

        public ParseResult(int pageCount, String text) {
            this.pageCount = pageCount;
            this.text = text;
        }

        public int getPageCount() {
            return pageCount;
        }

        public String getText() {
            return text;
        }
    }

    /**
     * 解析 PDF 文件。
     *
     * @param file PDF 文件路径
     * @return 解析结果（页数 + 抽取文本）
     * @throws IOException PDF 损坏 / 无法读取时抛出
     */
    public ParseResult parse(Path file) throws IOException {
        try (PDDocument document = PDDocument.load(file.toFile())) {
            int pageCount = document.getNumberOfPages();
            // 按坐标排序抽取，尽量还原阅读顺序
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            return new ParseResult(pageCount, text == null ? "" : text);
        }
    }
}
