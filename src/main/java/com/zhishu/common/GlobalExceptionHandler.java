package com.zhishu.common;

import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> biz(BizException e) {
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    /** 乐观锁冲突：工单状态被并发修改，提示重试而不是把堆栈抛给前端 */
    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ApiResponse<Void> optimisticLock(Exception e) {
        return ApiResponse.fail(409, "工单状态已被他人修改，请刷新后重试");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> other(Exception e) {
        log.error("unexpected error", e);
        return ApiResponse.fail(500, "服务繁忙，请稍后再试");
    }
}
