package com.shiftcla.app.data

import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 日期同步的单元测试。
 *
 * 全部用**显式构造的 Calendar**，不依赖"跑测试那天是周几"——
 * 否则测试会时好时坏（经典的 flaky test）。
 */
class ShiftclaDateTest {

    /** 造一个指定年月日的 Calendar（月按 1-12 传，内部转成 Calendar 的 0-11）。 */
    private fun cal(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day)
        }

    // ---------------------------------------------------------------- 星期几

    @Test
    fun `星期几编号与课程数据一致——周一到周日是 1 到 7`() {
        assertEquals(1, ShiftclaDate.dayOfWeekOf(cal(2026, 10, 5)), "2026-10-05 是周一")
        assertEquals(2, ShiftclaDate.dayOfWeekOf(cal(2026, 10, 6)), "2026-10-06 是周二")
        assertEquals(4, ShiftclaDate.dayOfWeekOf(cal(2026, 10, 1)), "2026-10-01 是周四")
        assertEquals(6, ShiftclaDate.dayOfWeekOf(cal(2026, 10, 3)), "2026-10-03 是周六")
        assertEquals(7, ShiftclaDate.dayOfWeekOf(cal(2026, 10, 4)), "2026-10-04 是周日")
    }

    @Test
    fun `周日不能算成 0 或 1——Calendar 里周日是 1 容易踩坑`() {
        val sunday = ShiftclaDate.dayOfWeekOf(cal(2026, 10, 4))
        assertEquals(7, sunday, "周日必须是 7，不是 1")
        assertTrue(sunday in 1..7)
    }

    // ---------------------------------------------------------------- 文案

    @Test
    fun `手机端文案是中文日期加今天`() {
        val s = ShiftclaDate.snapshot(cal(2026, 10, 1))
        assertEquals("10月1日 · 今天", s.currentDateLabel)
        assertEquals(4, s.dayOfWeek)
    }

    @Test
    fun `平板端胶囊保持设计稿的英文格式`() {
        val s = ShiftclaDate.snapshot(cal(2026, 10, 1))
        assertEquals("Oct 1", s.dateLabel)
        assertEquals("Thursday", s.weekdayLabel)
    }

    @Test
    fun `周次编号也能被取到——按周筛选靠它`() {
        assertEquals(1, ShiftclaDate.snapshot(cal(2026, 9, 14)).weekNumber)
        assertEquals(3, ShiftclaDate.snapshot(cal(2026, 10, 1)).weekNumber)
        assertEquals(null, ShiftclaDate.snapshot(cal(2026, 9, 7)).weekNumber, "预备周没有周次")
        assertTrue(
            ShiftclaDate.snapshot(cal(2026, 10, 1)).promptContext.contains("第 3 教学周"),
            "提示词要告诉模型现在是第几周",
        )
    }

    @Test
    fun `提示词上下文带上了星期几和编号`() {
        val ctx = ShiftclaDate.snapshot(cal(2026, 10, 1)).promptContext
        assertTrue(ctx.contains("2026年10月1日"), "应含完整日期：$ctx")
        assertTrue(ctx.contains("周四"), "应含中文星期：$ctx")
        assertTrue(ctx.contains("dayOfWeek=4"), "应含数值编号供模型对齐：$ctx")
    }

    @Test
    fun `学期周次从设定起点反推`() {
        // 校历：预备周 9.7–9.13（不算教学周）→ 第 1 周 9.14–9.20
        assertEquals(null, ShiftclaDate.snapshot(cal(2026, 9, 7)).weekLabel, "预备周不是教学周")
        assertEquals(null, ShiftclaDate.snapshot(cal(2026, 9, 13)).weekLabel, "预备周最后一天也不是")
        assertEquals("Week 1", ShiftclaDate.snapshot(cal(2026, 9, 14)).weekLabel, "9.14 才是第 1 周")
        assertEquals("Week 1", ShiftclaDate.snapshot(cal(2026, 9, 20)).weekLabel, "同一周内不变")
        assertEquals("Week 2", ShiftclaDate.snapshot(cal(2026, 9, 21)).weekLabel)
        assertEquals("Week 3", ShiftclaDate.snapshot(cal(2026, 10, 1)).weekLabel, "10.1 是第 3 周")
    }

    // ---------------------------------------------------------------- 学期阶段

    /**
     * 三个阶段的分界：**预备周算"还没开始"、学期最后一天算"期中"、第二天才算"已结束"**。
     *
     * 这三个边界是 `todayCoursesOf` 决定"到底显不显示课"的依据，
     * 也是界面文案（学期还未开始 / 今天没有课 / 学期已经过去了）的唯一开关。
     */
    @Test
    fun `学期阶段——开学前、教学周内、学期结束后`() {
        assertEquals(TermPhase.BeforeTerm, ShiftclaDate.snapshot(cal(2026, 9, 1)).phase)
        assertEquals(TermPhase.BeforeTerm, ShiftclaDate.snapshot(cal(2026, 9, 7)).phase, "预备周仍算未开始")
        assertEquals(TermPhase.BeforeTerm, ShiftclaDate.snapshot(cal(2026, 9, 13)).phase, "预备周最后一天也是未开始")
        assertEquals(TermPhase.InTerm, ShiftclaDate.snapshot(cal(2026, 9, 14)).phase, "9.14 是第 1 周周一")
        assertEquals(TermPhase.InTerm, ShiftclaDate.snapshot(cal(2027, 1, 10)).phase, "第 17 周周日 = 学期最后一天")
        assertEquals(TermPhase.AfterTerm, ShiftclaDate.snapshot(cal(2027, 1, 11)).phase, "学期结束后第一天")
        assertEquals(TermPhase.AfterTerm, ShiftclaDate.snapshot(cal(2027, 3, 1)).phase)
    }

    @Test
    fun `学期外不给出教学周编号，学期内才有`() {
        // 周次胶囊只该在教学周内出现，否则会出现"第 18 周"这种不存在的周
        assertNull(ShiftclaDate.snapshot(cal(2026, 9, 1)).weekNumber)
        assertNull(ShiftclaDate.snapshot(cal(2026, 9, 1)).weekLabel)
        assertNull(ShiftclaDate.snapshot(cal(2027, 1, 11)).weekNumber, "学期结束后不该还挂着周次")
        assertNull(ShiftclaDate.snapshot(cal(2027, 1, 11)).weekLabel)
        assertEquals("Week 17", ShiftclaDate.snapshot(cal(2027, 1, 10)).weekLabel)
        assertEquals(null, ShiftclaDate.snapshot(cal(2027, 1, 17)).weekNumber, "第 18 周不存在")
    }

    @Test
    fun `学期最后一天算得对——17 周学期到 2027 年 1 月 10 日`() {
        val end = ShiftclaDate.termEnd()
        assertNotNull(end)
        assertEquals(2027, end.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, end.get(Calendar.MONTH))
        assertEquals(10, end.get(Calendar.DAY_OF_MONTH))
        assertEquals(7, ShiftclaDate.dayOfWeekOf(end), "学期最后一天应是周日")
    }

    @Test
    fun `学期结束文案里带上结束日期`() {
        val s = ShiftclaDate.snapshot(cal(2027, 2, 4))
        assertEquals("2027年1月10日", s.termEndLabel)
        assertTrue(s.termKey.isNotBlank(), "termKey 是季节性文案的缓存键，不能为空")
    }

    // ---------------------------------------------------------------- 时间解析

    @Test
    fun `时间字符串转分钟用于排序`() {
        assertEquals(0, ShiftclaDate.minutesOf("00:00"))
        assertEquals(480, ShiftclaDate.minutesOf("08:00"))
        assertEquals(845, ShiftclaDate.minutesOf("14:05"))
        assertEquals(1245, ShiftclaDate.minutesOf("20:45"))
    }

    @Test
    fun `时间格式异常时不抛异常、排到最后`() {
        val worst = Int.MAX_VALUE
        assertEquals(worst, ShiftclaDate.minutesOf(""))
        assertEquals(worst, ShiftclaDate.minutesOf("下午两点"))
        assertEquals(worst, ShiftclaDate.minutesOf("8"))
        assertEquals(worst, ShiftclaDate.minutesOf("ab:cd"))
    }

    @Test
    fun `按开始时间排序——上午在前 且乱序输入也能排好`() {
        val raw = listOf("20:15", "08:00", "14:00", "11:45", "10:10")
        val sorted = raw.sortedBy { ShiftclaDate.minutesOf(it) }
        assertEquals(listOf("08:00", "10:10", "11:45", "14:00", "20:15"), sorted)
    }

    @Test
    fun `排序时非法时间沉底而不是插在中间`() {
        val raw = listOf("14:00", "上午", "08:00")
        assertEquals(listOf("08:00", "14:00", "上午"), raw.sortedBy { ShiftclaDate.minutesOf(it) })
    }
}
