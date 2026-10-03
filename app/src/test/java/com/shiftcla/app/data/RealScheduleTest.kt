package com.shiftcla.app.data

import com.shiftcla.app.ui.dashboard.Course
import com.shiftcla.app.ui.dashboard.todayCoursesOf
import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 用**真实课表数据**做的验收测试（2026-2027 秋季学期）。
 *
 * 值在于：这份数据把筛选逻辑的每个分支都覆盖到了 ——
 * 「第[2-9]周」的区间、**双周**、跨 14-17 周才开课的实践课，
 * 以及同一门课在多个星期/教室各排一节。纯造数据很难造得这么全。
 *
 * 期望值都是照着课表**手算**出来的，不是"跑一遍看结果再抄"。
 *
 * 调的是顶层纯函数 [todayCoursesOf] 而不是 `ShiftclaViewModel` ——
 * 显式传 [ShiftclaDate.Snapshot]，既不受"今天"影响，也不碰 Android 主线程调度器。
 */
class RealScheduleTest {

    private fun cal(y: Int, m: Int, d: Int) = Calendar.getInstance().apply {
        clear(); set(y, m - 1, d)
    }

    /** 造一个带具体时刻的 Calendar，用来验证"几点看课表"不影响周次。 */
    private fun calAt(y: Int, m: Int, d: Int, hour: Int, minute: Int) = Calendar.getInstance().apply {
        clear(); set(y, m - 1, d, hour, minute)
    }

    private fun c(
        name: String, dow: Int, startWeek: Int, endWeek: Int,
        startTime: String, endTime: String, place: String, parity: String? = null,
    ) = Course(
        courseName = name, dayOfWeek = dow,
        startWeek = startWeek, endWeek = endWeek, weekParity = parity,
        startTime = startTime, endTime = endTime, location = place,
    )

    /** 用户上传的 12 条课程记录，原样搬进来。 */
    private val mySchedule = listOf(
        c("Python程序设计", 4, 2, 9, "18:20", "21:35", "思源楼-B306"),
        c("计算机组成原理B", 2, 2, 12, "10:10", "11:50", "致用楼-302"),
        c("计算机组成原理B", 4, 2, 10, "08:10", "09:50", "致用楼-210", parity = "even"),
        c("数据结构与算法B", 1, 10, 13, "08:10", "11:50", "经世楼-519"),
        c("数据结构与算法B", 5, 10, 13, "08:10", "11:50", "经世楼-517"),
        c("数据库原理与设计B", 2, 2, 9, "18:20", "21:35", "博雅楼-B608"),
        c("程序设计基础B", 1, 2, 9, "13:30", "17:10", "思源楼-B206"),
        c("形势与政策X", 5, 5, 8, "13:30", "15:10", "经世楼-103"),
        c("马克思主义基本原理", 2, 2, 17, "08:10", "09:50", "经世楼-325"),
        c("马克思主义基本原理", 4, 2, 16, "15:30", "17:10", "经世楼-316", parity = "even"),
        c("程序设计综合实践B", 1, 14, 17, "08:10", "11:50", "经世楼-717"),
        c("程序设计综合实践B", 4, 14, 17, "08:10", "11:50", "经世楼-724"),
    )

    private fun names(list: List<Course>) = list.map { it.courseName }

    @Test
    fun `周四 第 3 周——两门双周课都不上，只剩晚上的 Python`() {
        // 2026-10-01 是周四，第 3 教学周（奇数周）
        val today = ShiftclaDate.snapshot(cal(2026, 10, 1))
        assertEquals(4, today.dayOfWeek)
        assertEquals(3, today.weekNumber)

        val got = todayCoursesOf(mySchedule, today)
        assertEquals(1, got.size, "实际：" + got.map { it.courseName + " " + it.startTime })
        assertEquals("Python程序设计", got[0].courseName)
        assertEquals("思源楼-B306", got[0].location)
    }

    @Test
    fun `周二 第 2 周——三门都要，且按节次先后排`() {
        val today = ShiftclaDate.snapshot(cal(2026, 9, 22))   // 周二，第 2 周
        assertEquals(2, today.dayOfWeek)
        assertEquals(2, today.weekNumber, "9.22 是第 2 教学周（第 1 周是 9.14–9.20）")

        val got = todayCoursesOf(mySchedule, today)
        assertEquals(
            listOf("马克思主义基本原理", "计算机组成原理B", "数据库原理与设计B"),
            names(got),
            "应按 1-2 节 → 3-4 节 → 9-12 节 排序",
        )
    }

    @Test
    fun `周四 第 10 周——Python 已结课，两门双周课本该上`() {
        // 第 10 周是偶数周，双周课生效
        val week10 = ShiftclaDate.snapshot(cal(2026, 11, 19))   // 周四
        assertEquals(4, week10.dayOfWeek)

        val got = todayCoursesOf(mySchedule, week10)
        assertEquals(
            listOf("计算机组成原理B", "马克思主义基本原理"),
            names(got),
            "Python 只到第 9 周该消失；两门双周课在偶数周该出现",
        )
    }

    @Test
    fun `第 16 周周四——实践课开课、马原双周生效、Python 早没了`() {
        val week16 = ShiftclaDate.snapshot(cal(2026, 12, 31))   // 周四，第 16 周
        assertEquals(4, week16.dayOfWeek)
        val got = todayCoursesOf(mySchedule, week16)
        // 第 16 周是偶数：马原周四双周 ✓、计组周四双周只到第 10 周 ✗、
        // 实践课第 14-17 周 ✓、Python 第 2-9 周 ✗
        // 排序按开始时间升序 → 实践课 08:10 在马原 15:30 之前
        assertEquals(
            listOf("程序设计综合实践B", "马克思主义基本原理"),
            names(got),
            "实际：" + got.map { it.courseName + " " + it.startTime + " " + it.location },
        )
    }

    @Test
    fun `同一门课在两个星期的记录不会被去重误删`() {
        // 数据结构与算法B：周一 1-4 节 @519、周五 1-4 节 @517。
        // 时间相同、教室不同；但如果哪天两间教室也一样，去重键漏了 dayOfWeek 就会误删一条。
        val sameTimeSamePlace = listOf(
            c("数据结构与算法B", 1, 10, 13, "08:10", "11:50", "经世楼-519"),
            c("数据结构与算法B", 5, 10, 13, "08:10", "11:50", "经世楼-519"),
        )
        val monday = ShiftclaDate.snapshot(cal(2026, 11, 16))   // 周一，第 10 周
        assertEquals(1, monday.dayOfWeek)
        val gotMon = todayCoursesOf(sameTimeSamePlace, monday)
        assertEquals(1, gotMon.size, "周一只该留周一那条")
        assertEquals(1, gotMon[0].dayOfWeek)

        val friday = ShiftclaDate.snapshot(cal(2026, 11, 20))   // 周五，第 10 周
        assertEquals(5, friday.dayOfWeek)
        assertEquals("经世楼-519", todayCoursesOf(sameTimeSamePlace, friday).single().location)
    }

    @Test
    fun `完全重复的记录才该被去掉`() {
        val dup = listOf(
            c("Python程序设计", 4, 2, 9, "18:20", "21:35", "思源楼-B306"),
            c("Python程序设计", 4, 2, 9, "18:20", "21:35", "思源楼-B306"),
        )
        val thursday = ShiftclaDate.snapshot(cal(2026, 10, 1))
        assertEquals(1, todayCoursesOf(dup, thursday).size, "完全相同的应合并成一条")
    }

    @Test
    fun `预备周既不显示课、也没有教学周编号`() {
        val prep = ShiftclaDate.snapshot(cal(2026, 9, 10))   // 预备周内的周四
        assertEquals(4, prep.dayOfWeek)
        assertNull(prep.weekNumber, "预备周不该有教学周编号")
        assertEquals(TermPhase.BeforeTerm, prep.phase)
        // 关键行为变更：预备周**还没开课**，宁可空着让界面显示"学期还未开始"，
        // 也不要把"开学第一周才上"的安排列出来。以前的实现是"周次未知就不按周过滤"，
        // 结果周四 4 条记录全显示 —— 用户看到的是一屏其实还没开始的课。
        assertTrue(
            todayCoursesOf(mySchedule, prep).isEmpty(),
            "预备周不该显示任何课，实际：" + todayCoursesOf(mySchedule, prep).map { it.courseName },
        )
    }

    @Test
    fun `学期结束后不再把上学期的旧课摆出来`() {
        val holiday = ShiftclaDate.snapshot(cal(2027, 2, 4))   // 学期结束后（第 17 周之后）
        assertEquals(TermPhase.AfterTerm, holiday.phase)
        assertNull(holiday.weekNumber)
        // 整份课表都已过期 —— 界面这一层要空出来，才能显示"学期已经过去了，
        // 记得导入新学期课表"那句提示。
        assertTrue(todayCoursesOf(mySchedule, holiday).isEmpty())
    }

    @Test
    fun `学期最后一天仍然正常显示课表`() {
        // 2027-01-10 是第 17 周周日 = 学期最后一天，仍在学期内
        val lastDay = ShiftclaDate.snapshot(cal(2027, 1, 10))
        assertEquals(TermPhase.InTerm, lastDay.phase)
        assertEquals(7, lastDay.dayOfWeek, "那天是周日")
        assertNotNull(lastDay.weekNumber, "学期最后一天仍该有周次")
    }

    @Test
    fun `几点看课表不该改变教学周——晚上 23 点也还是同一天`() {
        // 预备周最后一天（9.13 周日）：不管当天几点看，都还没进第 1 教学周。
        // 之前 termWeekOf 拿"毫秒差"直接除以一天的毫秒数、且用向零截断的除法，
        // 23:00 时天数为 -1 小时 → 截断成 0 天 → 误报第 1 周。
        for (hour in listOf(0, 9, 12, 23)) {
            assertNull(
                ShiftclaDate.snapshot(calAt(2026, 9, 13, hour, 30)).weekNumber,
                "9.13 的 $hour:30 不该算出教学周",
            )
        }
        // 第 1 周周一当天，全天任何时刻都应是第 1 周
        for (hour in listOf(0, 9, 23)) {
            assertEquals(
                1,
                ShiftclaDate.snapshot(calAt(2026, 9, 14, hour, 30)).weekNumber,
                "9.14 的 $hour:30 都应是第 1 周",
            )
        }
        // 跨周边界同理：9.20 周日仍第 1 周、9.21 周一进第 2 周
        assertEquals(1, ShiftclaDate.snapshot(calAt(2026, 9, 20, 23, 59)).weekNumber)
        assertEquals(2, ShiftclaDate.snapshot(calAt(2026, 9, 21, 0, 1)).weekNumber)
    }
}
