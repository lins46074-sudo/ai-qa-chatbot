package com.aidoc.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

/**
 * 全局异常处理器（{@code @RestControllerAdvice}）。
 *
 * <p>按优先级依次处理各类型异常，统一转换为 Result 结构返回（HTTP 200 + body.code），
 * 每个分支均记录 error/warn 日志便于排查。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 1. 业务异常：携带业务状态码原样透出。
     *
     * @param e 业务异常
     * @return 统一返回体
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("[业务异常] code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 2. 方法参数校验异常（@RequestBody + @Valid 触发）：取首个字段校验错误提示。
     *
     * @param e 方法参数校验异常
     * @return 统一返回体（code=400）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = firstFieldErrorMessage(e.getBindingResult().getFieldErrors());
        log.warn("[参数校验失败] {}", message);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 3a. 表单绑定异常（form 表单 / @ModelAttribute 校验失败）：同样取首条错误提示。
     *
     * @param e 绑定异常
     * @return 统一返回体（code=400）
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = firstFieldErrorMessage(e.getBindingResult().getFieldErrors());
        log.warn("[参数绑定失败] {}", message);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 3b. 参数约束违规异常（方法入参直接加 @Validated + @NotNull 等触发）。
     *
     * @param e 约束违规异常
     * @return 统一返回体（code=400）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(jakarta.validation.ConstraintViolation::getMessage)
                .orElse(ResultCode.BAD_REQUEST.getMessage());
        log.warn("[参数约束违规] {}", message);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 4. 缺少必要请求参数。
     *
     * @param e 缺少参数异常
     * @return 统一返回体（code=400）
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        String message = "缺少必要请求参数: " + e.getParameterName();
        log.warn("[缺少参数] {}", message);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 5. 参数类型错误（如把 "abc" 绑定到 Long 路径/查询参数）。
     *
     * @param e 类型不匹配异常
     * @return 统一返回体（code=400）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "参数类型错误: " + e.getName();
        log.warn("[参数类型错误] {}", message);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 6. 请求体格式错误（JSON 语法错误等）。
     *
     * @param e 请求体不可读异常
     * @return 统一返回体（code=400）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[请求体格式错误] {}", e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST.getCode(), "请求体格式错误");
    }

    /**
     * 7. 上传文件超过大小限制：提示改用分片上传。
     *
     * @param e 上传大小超限异常
     * @return 统一返回体（code=400）
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("[上传超限] {}", e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST.getCode(), "文件超过大小限制，请使用分片上传");
    }

    /**
     * 8. 数据库唯一键冲突（如用户名重复）。
     *
     * @param e 重复键异常
     * @return 统一返回体（code=400）
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("[数据重复] {}", e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST.getCode(), "数据已存在，请勿重复提交");
    }

    /**
     * 9. 兜底：其余一切未知异常统一按系统错误处理（HTTP 200 + code=500）。
     *
     * @param e 未知异常
     * @return 统一返回体（code=500）
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("[系统异常]", e);
        return Result.error(ResultCode.SYSTEM_ERROR);
    }

    /**
     * 从字段错误列表中取第一条的默认提示；列表为空时回退为通用提示。
     *
     * @param fieldErrors 字段错误列表
     * @return 错误提示文本
     */
    private String firstFieldErrorMessage(List<FieldError> fieldErrors) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return ResultCode.BAD_REQUEST.getMessage();
        }
        FieldError first = fieldErrors.get(0);
        return first.getDefaultMessage() != null ? first.getDefaultMessage() : ResultCode.BAD_REQUEST.getMessage();
    }
}
