package com.zhishu.common;

/** ThreadLocal 保存当前登录用户，注意 SSE 异步线程不会自动继承，需在提交异步任务前显式取值传递 */
public final class UserContext {
    private static final ThreadLocal<Long> USER = new ThreadLocal<>();
    public static void set(Long userId) { USER.set(userId); }
    public static Long get() { return USER.get(); }
    public static Long require() {
        Long id = USER.get();
        if (id == null) throw new BizException(401, "未登录");
        return id;
    }
    public static void clear() { USER.remove(); }
}
