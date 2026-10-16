package com.shiftcla.app.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 「清除所有课程」本地意图识别的单测。
 *
 * 背景：用户说「清除所有课程」，结果 App 把它当一段教务文本发给 AI 去"找课程"。
 * 修复是在 [ShiftclaViewModel.importSchedule] 发请求**之前**先本地识别该意图，
 * 命中就走 [ShiftclaViewModel.clearAllCourses]，根本不让它进 AI。
 *
 * 这里钉住顶层纯函数 `isClearAllIntent` 的判断边界：
 * 命中该清空的、以及不该误伤（提问 / 单门删除 / 正常课表文本）的。
 */
class ClearAllIntentTest {

    @Test
    fun `清空所有课程 命中`() {
        assertTrue(isClearAllIntent("清除所有课程"))
        assertTrue(isClearAllIntent("清空所有课程"))
        assertTrue(isClearAllIntent("删除全部课表"))
        assertTrue(isClearAllIntent("把课表全部清空"))
        assertTrue(isClearAllIntent("重置整个课表"))
        assertTrue(isClearAllIntent("所有课程都删掉"))
    }

    @Test
    fun `口语化表达也命中`() {
        assertTrue(isClearAllIntent("帮我清空课表"))
        assertTrue(isClearAllIntent("课表不要了"))
        assertTrue(isClearAllIntent("把这些课全部移除"))
    }

    @Test
    fun `提问不该误触发`() {
        assertFalse(isClearAllIntent("怎么清空课程？"))
        assertFalse(isClearAllIntent("如何删除所有课程"))
        assertFalse(isClearAllIntent("为什么我的课程没了"))
    }

    @Test
    fun `单门课程删除不是清空全部`() {
        // 「删掉高数」是单门删除，应该走 AI 增量修改，而不是本地清空全部
        assertFalse(isClearAllIntent("删掉高等数学"))
        assertFalse(isClearAllIntent("把线性代数移除"))
    }

    @Test
    fun `正常课表文本不误伤`() {
        // 真实教务文本里出现「课」「课程」字眼，绝不能当成清空指令
        assertFalse(isClearAllIntent("周一 1-2节 高等数学 教学楼A101 王老师"))
        assertFalse(isClearAllIntent("本学期课程表如下：..."))
        assertFalse(isClearAllIntent(""))
        assertFalse(isClearAllIntent("   "))
    }

    // ------------------------------------------------------------------ 英文（国际化后新增）

    @Test
    fun `英文的清空指令也能命中`() {
        assertTrue(isClearAllIntent("clear all courses"))
        assertTrue(isClearAllIntent("Clear all my classes"))
        assertTrue(isClearAllIntent("delete all classes"))
        assertTrue(isClearAllIntent("remove everything"))
        assertTrue(isClearAllIntent("reset the whole schedule"))
        assertTrue(isClearAllIntent("wipe my timetable"))
    }

    @Test
    fun `英文提问不该误触发`() {
        assertFalse(isClearAllIntent("How do I clear all courses?"))
        assertFalse(isClearAllIntent("Why are my classes gone"))
    }

    @Test
    fun `英文单门课删除不是清空全部`() {
        assertFalse(isClearAllIntent("delete Calculus"))
        assertFalse(isClearAllIntent("remove Linear Algebra from Monday"))
    }

    @Test
    fun `英文课表文本不误伤`() {
        // 含 all / class 字样的正常课表内容，不能当成清空指令
        assertFalse(isClearAllIntent("Mon 8:00 Calculus Room A101 Prof. Wang"))
        assertFalse(isClearAllIntent("Small Group Seminar, Wed 14:00"))
    }
}
