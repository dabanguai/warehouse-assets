package com.yingchang.warehouse;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import static com.yingchang.warehouse.Data.of;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(BusinessError.class) ResponseEntity<Data> business(BusinessError e) { return ResponseEntity.status(e.status).body(of("code", e.code, "message", e.getMessage(), "details", e.details)); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<Data> conflict() { return business(new BusinessError("DATA_CONFLICT", "名称、编号或单号重复，或关联资料无效，请核对")); }
    @ExceptionHandler(UncategorizedSQLException.class) ResponseEntity<Data> sql(UncategorizedSQLException e) {
        // MySQL 的 CHECK 约束使用 HY000，JDBC 不会将其归入完整性约束异常。
        return e.getSQLException().getErrorCode() == 3819 ? conflict() : unexpected(e);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class, MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Data> validation() { return business(new BusinessError("VALIDATION_ERROR", "请检查必填项和填写格式", 400)); }
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<Data> size() { return business(new BusinessError("FILE_SIZE", "文件不能超过20MB", 413)); }
    @ExceptionHandler(NoResourceFoundException.class) ResponseEntity<Data> missing() { return business(new BusinessError("NOT_FOUND", "记录不存在", 404)); }
    @ExceptionHandler(Exception.class) ResponseEntity<Data> unexpected(Exception e) { return business(new BusinessError("SERVER_ERROR", "服务暂时不可用，请联系管理员", 500)); }
}
