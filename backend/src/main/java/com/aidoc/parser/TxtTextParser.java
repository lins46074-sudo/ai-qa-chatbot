package com.aidoc.parser;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * TXT 文本解析器。
 *
 * <p>优先按 UTF-8 严格解码；失败（说明文件非 UTF-8，常见于 GBK 编码的
 * Windows 文本文档）则回退 GBK，保证中文文档兼容。</p>
 */
@Component
public class TxtTextParser {

    /**
     * 读取 TXT 文件内容（自动识别 UTF-8 / GBK）。
     *
     * @param file TXT 文件路径
     * @return 解码后的全文
     * @throws IOException 读取失败时抛出
     */
    public String parse(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        try {
            return strictDecode(bytes, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            // UTF-8 严格解码失败 → 回退 GBK（覆盖中文环境常见编码）
            return strictDecode(bytes, Charset.forName("GBK"));
        }
    }

    /**
     * 按指定字符集严格解码（遇到非法字节序列即抛异常，用于编码探测）。
     *
     * @param bytes   原始字节
     * @param charset 目标字符集
     * @return 解码字符串
     * @throws CharacterCodingException 字节序列非法时抛出
     */
    private String strictDecode(byte[] bytes, Charset charset) throws CharacterCodingException {
        CharBuffer decoded = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes));
        return decoded.toString();
    }
}
