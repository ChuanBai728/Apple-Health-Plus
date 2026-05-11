package app.healthplus.api.config;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 * <p>
 * 统一拦截控制器层抛出的各类异常，将其转换为结构化的 JSON 错误响应，
 * 包含 timestamp、status、error、message 四个字段。
 * <p>
 * 设计思路：
 * <ul>
 *   <li>业务异常（IllegalArgumentException 等）返回 4xx，不打印堆栈，避免日志噪音</li>
 *   <li>系统异常（未捕获的 Exception）返回 500 并记录完整堆栈，便于排查</li>
 *   <li>安全异常（AuthenticationException / AccessDeniedException）映射为 401/403</li>
 *   <li>字段校验异常会将英文字段名翻译为中文，提升前端展示友好度</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务参数非法异常（如参数越界、格式不合法）。
     * 返回 400 Bad Request。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * 处理内部状态异常（如服务未就绪、依赖不可用）。
     * 返回 500 Internal Server Error，并记录完整堆栈。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.error("Internal error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage() != null ? ex.getMessage() : "Internal server error");
    }

    /**
     * 处理 Bean Validation 约束违反异常（如 @NotNull、@Size 注解校验失败）。
     * 将所有违反项拼接为一条消息返回 400。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        log.warn("Validation error: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /** 字段名中英文映射表，用于将校验错误中的英文字段名翻译为中文 */
    private static final Map<String, String> FIELD_CN = Map.of(
            "username", "用户名", "password", "密码", "email", "邮箱");

    /**
     * 处理 @Valid 注解触发的请求体校验异常。
     * 将每个字段错误翻译为"中文字段名: 错误信息"格式，拼接后返回 400。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> FIELD_CN.getOrDefault(e.getField(), e.getField()) + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("验证失败");
        log.warn("Validation error: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 处理请求体格式错误（如 JSON 语法错误、类型不匹配导致反序列化失败）。
     * 返回 400，不暴露内部细节。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    /**
     * 处理请求参数类型不匹配（如期望 UUID 传入了非法字符串）。
     * 返回 400 并提示具体的参数名和传入值。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
        log.warn("Type mismatch: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 处理文件上传大小超限异常。
     * 返回 413 Payload Too Large。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "File size exceeds maximum limit");
    }

    /**
     * 处理静态资源不存在异常（如访问不存在的 API 路径）。
     * 返回 404，使用 debug 级别记录以减少日志噪音。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "Resource not found");
    }

    /**
     * 兜底处理器：捕获所有未被上面特定 Handler 处理的异常。
     * 返回 500 并记录完整堆栈，对外只暴露通用错误信息，避免泄露内部实现。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    /**
     * 处理授权失败异常（已认证但权限不足）。
     * 返回 403 Forbidden。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied: " + ex.getMessage());
    }

    /**
     * 处理认证失败异常（未提供有效 Token 或 Token 已过期）。
     * 返回 401 Unauthorized。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Authentication failed: " + ex.getMessage());
    }

    /**
     * 构建统一的错误响应体。
     * 使用 LinkedHashMap 保证字段顺序：timestamp -> status -> error -> message。
     *
     * @param status  HTTP 状态码
     * @param message 错误描述信息
     * @return 包含错误详情的 ResponseEntity
     */
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
