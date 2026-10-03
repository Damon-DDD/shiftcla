package com.shiftcla.app.ui.dashboard

import com.shiftcla.app.data.TermNote
import com.shiftcla.app.data.TermPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 空态文案的分支测试。
 *
 * 这四段文案是用户逐条指定的，属于**产品行为**而不是实现细节 ——
 * 所以用断言钉住，避免以后有人顺手"优化"掉。
 */
class EmptyCopyTest {

    @Test
    fun `预备周说学期还未开始`() {
        val c = emptyCopyFor(TermPhase.BeforeTerm, hasAnyCourses = true, termNote = null)
        assertEquals("学期还未开始", c.title)
        assertEquals(EmptyKind.Calendar, c.kind, "学期前用日历图标，不是 AI 星标")
        assertTrue(c.body.contains("教务数据"), "要给出下一步动作，不能只说'还没开始'：${c.body}")
    }

    @Test
    fun `学期结束用 AI 写的那句话`() {
        val note = "考完了，先去睡一觉，新学期课表出来记得导进来"
        val c = emptyCopyFor(TermPhase.AfterTerm, hasAnyCourses = true, termNote = note)
        assertEquals("学期已经过去了", c.title)
        assertEquals(note, c.body)
    }

    @Test
    fun `AI 文案还没回来时用兜底，界面不留空白`() {
        // null = 正在请求；空串/空白 = 模型返回了垃圾。三种都必须兜住
        for (note in listOf(null, "", "   ")) {
            val c = emptyCopyFor(TermPhase.AfterTerm, hasAnyCourses = true, termNote = note)
            assertEquals(TermNote.FALLBACK, c.body, "note=[$note] 时应退回兜底文案")
            assertTrue(c.body.isNotBlank())
        }
    }

    @Test
    fun `学期中 今天没课 和 一张课都没有 是两种完全不同的文案`() {
        val noClassToday = emptyCopyFor(TermPhase.InTerm, hasAnyCourses = true, termNote = null)
        val noSchedule = emptyCopyFor(TermPhase.InTerm, hasAnyCourses = false, termNote = null)

        assertEquals("今天没有课", noClassToday.title)
        assertEquals("还没有课表", noSchedule.title)
        assertTrue(noClassToday.title != noSchedule.title, "这两种情况混用会让用户搞不清是没课还是没数据")
        assertTrue(noSchedule.body.contains("导入教务数据"), "没数据时要引导去导入")
        assertTrue(!noClassToday.body.contains("导入教务数据"), "只是今天没课，别让用户以为要重新导入")
    }

    @Test
    fun `学期外的文案只由阶段决定，不看有没有课表`() {
        // 学期前/后是"时间维度"的判断，优先级高于"有没有数据"——
        // 否则寒暑假打开 App 会因为库里还有旧课表而显示成"今天没有课"，那是错的。
        for (phase in listOf(TermPhase.BeforeTerm, TermPhase.AfterTerm)) {
            val withData = emptyCopyFor(phase, hasAnyCourses = true, termNote = "x")
            val noData = emptyCopyFor(phase, hasAnyCourses = false, termNote = "x")
            assertEquals(withData, noData, "$phase 不该因为有没有课表而换文案")
        }
    }

    @Test
    fun `滑到别的日子后——今天没有课 要变成 这天没有课`() {
        // 左右滑切日是"浏览历史/未来"，文案不能还挂在"今天"上撒谎
        val today = emptyCopyFor(TermPhase.InTerm, hasAnyCourses = true, termNote = null, isToday = true)
        val otherDay = emptyCopyFor(TermPhase.InTerm, hasAnyCourses = true, termNote = null, isToday = false)

        assertEquals("今天没有课", today.title)
        assertEquals("这天没有课", otherDay.title)
        assertTrue(otherDay.body.contains("回到当天课程"), "要引导用户用悬浮按钮回去：${otherDay.body}")

        // 没有课表（hasAnyCourses=false）与日期无关：不管滑到哪天都是"还没有课表"
        val noSchedule = emptyCopyFor(TermPhase.InTerm, hasAnyCourses = false, termNote = null, isToday = false)
        assertEquals("还没有课表", noSchedule.title)
    }
}
