package com.aidoc.ai;

import com.aidoc.rag.RetrievedChunk;

import java.util.List;

/**
 * Prompt 组装器。
 *
 * <p>负责把「系统指令 + 文档上下文」拼装为发给大模型的消息序列。提供三种组装方式：</p>
 * <ul>
 *   <li>{@link #buildSystemPrompt}：<b>RAG 模式（默认）</b>，只注入检索命中的若干片段，
 *       token 成本低、上下文聚焦，并带引用编号；</li>
 *   <li>{@link #buildFullTextSystemPrompt}：<b>全文注入模式（降级）</b>，把整篇文档截断后注入，
 *       保留该路径是为了与 RAG 做效果对比（见 aidoc.rag.enabled 开关）；</li>
 *   <li>{@link #buildNoEvidenceSystemPrompt}：检索无命中但仍需调用模型时的提示词。</li>
 * </ul>
 *
 * <p>RAG 模式通过四条硬性约束抑制幻觉：
 * <ol>
 *   <li><b>知识边界</b>：只允许依据参考资料作答，禁止使用模型自身知识；</li>
 *   <li><b>强制引用</b>：每处结论必须标注 {@code [编号]}，使答案可被逐句核对；</li>
 *   <li><b>允许弃答</b>：资料不足时必须回答「文档中未找到相关信息」，
 *       显式给模型一条「不回答」的出路 —— 这是降低编造率最有效的一招；</li>
 *   <li><b>零召回拒答</b>：检索为空时由上层直接返回固定文案，<b>根本不调用大模型</b>，
 *       从源头杜绝无依据生成（见 aidoc.rag.refuse-when-empty）。</li>
 * </ol></p>
 */
public final class PromptAssembler {

    /** 检索无命中时的固定回复（拒答模式下直接返回，不调用大模型） */
    public static final String NO_EVIDENCE_ANSWER =
            "文档中未找到相关信息。建议换一种问法，或确认该内容是否包含在本文档中。";

    /**
     * 私有构造器：工具类禁止实例化。
     */
    private PromptAssembler() {
    }

    /**
     * 构造 RAG 模式的系统提示词：注入带编号的检索片段。
     *
     * @param fileName 文档名（用于提示模型当前回答的是哪篇文档）
     * @param chunks   检索命中的片段（已编号）
     * @return 系统提示词
     */
    public static String buildSystemPrompt(String fileName, List<RetrievedChunk> chunks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是企业私有文档问答助手，正在回答关于文档《").append(fileName).append("》的问题。\n\n")
                .append("【回答规则】\n")
                .append("1. 只能依据下方【参考资料】作答，禁止使用参考资料之外的任何知识，禁止推测与编造；\n")
                .append("2. 每一处结论都必须标注依据来源，格式为 [编号]，例如：系统支持 PDF 与 TXT 两种格式[1]；\n")
                .append("3. 若参考资料不足以回答该问题，只回答「文档中未找到相关信息」，不要给出近似或推测性答案；\n")
                .append("4. 使用中文回答，条理清晰，需要时分点输出。\n\n")
                .append("【参考资料】\n");
        for (RetrievedChunk chunk : chunks) {
            prompt.append("[").append(chunk.getRefIndex()).append("] ")
                    .append(chunk.getContent()).append("\n\n");
        }
        return prompt.toString().trim();
    }

    /**
     * 构造全文注入模式（降级）的系统提示词。
     *
     * @param fileName 文档名
     * @param excerpt  文档摘录（已按 maxChars 截断）
     * @return 系统提示词
     */
    public static String buildFullTextSystemPrompt(String fileName, String excerpt) {
        return "你是企业私有文档问答助手。用户上传了一份文档《" + fileName + "》。\n"
                + "请严格依据下面的【文档内容】回答用户问题；若文档未覆盖相关问题，"
                + "请明确回答“文档中未找到相关信息”，绝对不要编造。\n"
                + "回答请使用中文，条理清晰，需要时分点输出。\n\n"
                + "【文档内容开始】\n" + excerpt + "\n【文档内容结束】";
    }

    /**
     * 构造检索无命中、但仍调用模型时的系统提示词。
     *
     * @param fileName 文档名
     * @return 系统提示词
     */
    public static String buildNoEvidenceSystemPrompt(String fileName) {
        return "你是企业私有文档问答助手，当前处理文档《" + fileName + "》。\n"
                + "本轮未检索到与该问题相关的文档片段，请如实告知用户文档中未找到相关信息，不要编造内容。";
    }

    /**
     * 超长文档摘录截断（仅全文注入模式使用）：总长 ≤ maxChars 时原文返回；
     * 超过时取头部 60% 与尾部 40%（中间以省略标记衔接），
     * 保证首尾关键信息（概述与结论）都不丢失。
     *
     * @param text     文档全文
     * @param maxChars 允许的最大字符数
     * @return 摘录文本
     */
    public static String buildExcerpt(String text, int maxChars) {
        if (text == null || text.isEmpty()) {
            return "(文档内容为空)";
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        int head = (int) (maxChars * 0.6);
        int tail = maxChars - head;
        return text.substring(0, head)
                + "\n\n……[文档中间内容过长，已省略……]\n\n"
                + text.substring(text.length() - tail);
    }
}
