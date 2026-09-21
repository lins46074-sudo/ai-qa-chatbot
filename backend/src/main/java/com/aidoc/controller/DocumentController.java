package com.aidoc.controller;

import com.aidoc.common.PageResult;
import com.aidoc.common.Result;
import com.aidoc.dto.ChunkMergeDTO;
import com.aidoc.service.DocumentService;
import com.aidoc.util.UserContext;
import com.aidoc.vo.ChunkStatusVO;
import com.aidoc.vo.DocumentVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档控制器：直传 / 分片上传（断点续传）/ 解析 / 列表 / 删除。
 *
 * <p>分片上传三段式流程：
 * <ol>
 *   <li>GET /chunk/status 查询已上传分片（秒传 / 断点续传依据）；</li>
 *   <li>POST /chunk/upload 逐片上传（可重复，幂等）；</li>
 *   <li>POST /chunk/merge 合并并解析。</li>
 * </ol></p>
 */
@Validated
@RestController
@RequestMapping("/api/doc")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 我的文档分页列表。
     *
     * @param current  页码
     * @param size     每页条数
     * @param keyword  文件名关键字（可空）
     * @param fileType 类型 PDF/TXT（可空）
     * @param status   解析状态（可空）
     * @return 文档分页
     */
    @GetMapping("/page")
    public Result<PageResult<DocumentVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) Integer status) {
        return Result.ok(documentService.pageDocs(UserContext.currentUserId(), current, size,
                keyword, fileType, status));
    }

    /**
     * 文档详情。
     *
     * @param id 文档 ID
     * @return 文档视图
     */
    @GetMapping("/{id}")
    public Result<DocumentVO> detail(@PathVariable Long id) {
        return Result.ok(documentService.getDoc(UserContext.currentUserId(), id));
    }

    /**
     * 删除文档（级联删除会话 / 消息 / 文件 / 缓存）。
     *
     * @param id 文档 ID
     * @return 成功提示
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.deleteDoc(UserContext.currentUserId(), id);
        return Result.ok("删除成功", null);
    }

    /**
     * 小文件直传（≤ 单文件限制，超出请走分片）。
     *
     * @param file 上传文件（PDF/TXT）
     * @return 文档视图（同步解析完成）
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DocumentVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(documentService.upload(UserContext.currentUserId(), file));
    }

    /**
     * 分片上传前置查询：断点续传时返回已上传分片序号。
     *
     * @param identifier 文件 MD5
     * @return 已上传分片信息
     */
    @GetMapping("/chunk/status")
    public Result<ChunkStatusVO> chunkStatus(
            @RequestParam @NotBlank(message = "分片标识不能为空") String identifier) {
        return Result.ok(documentService.chunkStatus(UserContext.currentUserId(), identifier));
    }

    /**
     * 上传单个分片。
     *
     * @param file        分片内容
     * @param identifier  文件 MD5
     * @param index       分片序号（0 起）
     * @param totalChunks 总分片数
     * @return 成功提示
     */
    @PostMapping(value = "/chunk/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Void> uploadChunk(@RequestParam("file") MultipartFile file,
                                    @RequestParam("identifier") String identifier,
                                    @RequestParam("index") int index,
                                    @RequestParam("totalChunks") int totalChunks) {
        documentService.uploadChunk(UserContext.currentUserId(), file, identifier, index, totalChunks);
        return Result.ok("分片上传成功", null);
    }

    /**
     * 合并分片（完整性 + MD5 双重校验后解析）。
     *
     * @param dto 合并参数
     * @return 文档视图（含解析状态）
     */
    @PostMapping("/chunk/merge")
    public Result<DocumentVO> mergeChunks(@Valid @RequestBody ChunkMergeDTO dto) {
        return Result.ok(documentService.mergeChunks(UserContext.currentUserId(), dto));
    }
}
