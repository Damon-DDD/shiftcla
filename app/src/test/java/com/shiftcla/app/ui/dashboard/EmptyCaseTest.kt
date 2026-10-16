package com.shiftcla.app.ui.dashboard

import com.shiftcla.app.data.TermPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 空态**分支**测试。
 *
 * 改造前这里断言的是具体中文句子；文案搬进 strings.xml 之后，断言对象换成了
 * [EmptyCase] —— 这样测的才是"哪个场景该显示哪种空态"这层产品行为，
 * 以后改一个字都不会误报。文案本身由截图测试兜住。
 */
class EmptyCaseTest {

    @Test
    fun `预备周说学期还未开始`() {
        val c = emptyCaseFor(TermPhase.BeforeTerm, hasAnyCourses = true)
        assertEquals(EmptyCase.BeforeTerm, c)
    }

    @Test
    fun `学期结束后是 AfterTerm，正文由 termNote 决定`() {
        assertEquals(
            EmptyCase.AfterTerm,
            emptyCaseFor(TermPhase.AfterTerm, hasAnyCourses = true),
        )
    }

    @Test
    fun `学期中 今天没课 和 一张课都没有 是两种完全不同的空态`() {
        val noClassToday = emptyCaseFor(TermPhase.InTerm, hasAnyCourses = true, isToday = true)
        val noSchedule = emptyCaseFor(TermPhase.InTerm, hasAnyCourses = false)

        assertEquals(EmptyCase.NoClassToday, noClassToday)
        assertEquals(EmptyCase.NoSchedule, noSchedule)
        assertNotEquals(
            noClassToday,
            noSchedule,
            "这两种混用会让用户搞不清是没课还是没数据",
        )
    }

    @Test
    fun `学期外的空态只由阶段决定，不看有没有课表`() {
        // 学期前/后是"时间维度"的判断，优先级高于"有没有数据"——
        // 否则寒暑假打开 App 会因为库里还有旧课表而显示成"今天没有课"，那是错的。
        for (phase in listOf(TermPhase.BeforeTerm, TermPhase.AfterTerm)) {
            assertEquals(
                emptyCaseFor(phase, hasAnyCourses = true),
                emptyCaseFor(phase, hasAnyCourses = false),
                "$phase 不该因为有没有课表而换空态",
            )
        }
    }

    @Test
    fun `滑到别的日子后——今天没有课 要变成 这天没有课`() {
        // 左右滑切日是"浏览历史/未来"，文案不能还挂在"今天"上撒谎
        assertEquals(
            EmptyCase.NoClassToday,
            emptyCaseFor(TermPhase.InTerm, hasAnyCourses = true, isToday = true),
        )
        assertEquals(
            EmptyCase.NoClassThatDay,
            emptyCaseFor(TermPhase.InTerm, hasAnyCourses = true, isToday = false),
        )

        // 没有课表（hasAnyCourses=false）与日期无关：不管滑到哪天都是"还没有课表"
        assertEquals(
            EmptyCase.NoSchedule,
            emptyCaseFor(TermPhase.InTerm, hasAnyCourses = false, isToday = false),
        )
    }
}
