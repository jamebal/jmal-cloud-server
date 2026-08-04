package com.jmal.clouddisk.util;

import com.jmal.clouddisk.exception.CommonException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 针对 {@link FileNameUtils#checkPath(String)} 的路径穿越 (CWE-22) 回归测试。
 */
class FileNameUtilsTest {

    @Test
    void checkPath_shouldRejectClassicTraversal() {
        assertThrows(CommonException.class, () -> FileNameUtils.checkPath("/a/../b"));
        assertThrows(CommonException.class, () -> FileNameUtils.checkPath("a\\..\\b"));
    }

    @Test
    void checkPath_shouldRejectTrailingDotDot() {
        // 末尾裸 ".." 不含分隔符, 是旧黑名单实现的绕过点
        assertThrows(CommonException.class, () -> FileNameUtils.checkPath("/.."));
        assertThrows(CommonException.class, () -> FileNameUtils.checkPath("/subdir/.."));
        assertThrows(CommonException.class, () -> FileNameUtils.checkPath("subdir/.."));
        assertThrows(CommonException.class, () -> FileNameUtils.checkPath("subdir\\.."));
        assertThrows(CommonException.class, () -> FileNameUtils.checkPath(".."));
    }

    @Test
    void checkPath_shouldRejectNullByte() {
        assertThrows(CommonException.class, () -> FileNameUtils.checkPath("/a\0b"));
    }

    @Test
    void checkPath_shouldAllowLegitimatePaths() {
        assertDoesNotThrow(() -> FileNameUtils.checkPath(null));
        assertDoesNotThrow(() -> FileNameUtils.checkPath(""));
        assertDoesNotThrow(() -> FileNameUtils.checkPath("/user/docs/report.pdf"));
        // "..txt"、"a..b" 等含连续点但不是独立 ".." 段的合法名称不应被误拦
        assertDoesNotThrow(() -> FileNameUtils.checkPath("/user/..hidden"));
        assertDoesNotThrow(() -> FileNameUtils.checkPath("/user/a..b/file"));
    }
}
