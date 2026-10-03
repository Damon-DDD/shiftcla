package com.shiftcla.app.data

import com.shiftcla.app.ui.dashboard.Course
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 教学周次筛选。
 *
 * 这是"别的周课程混进本周"的直接防线：教务课表里满是「1-16周」「单周」「双周」，
 * 光按 dayOfWeek 筛是筛不清楚的。
 */
class CourseWeekTest {

    private fun course(
        startWeek: Int = 1,
        endWeek: Int = 20,
        weekParity: String? = null,
    ) = Course(
        courseName = "测试课", dayOfWeek = 3,
        startTime = "08:00", endTime = "09:35",
        startWeek = startWeek, endWeek = endWeek, weekParity = weekParity,
    )

    @Test
    fun `区间内上、区间外不上`() {
        val c = course(startWeek = 3, endWeek = 8)
        assertFalse(c.matchesWeek(2), "第 2 周还没开课")
        assertTrue(c.matchesWeek(3), "起始周要包含")
        assertTrue(c.matchesWeek(5))
        assertTrue(c.matchesWeek(8), "结束周要包含")
        assertFalse(c.matchesWeek(9), "第 9 周已经结课")
    }

    @Test
    fun `单周课只在奇数周`() {
        val c = course(startWeek = 1, endWeek = 16, weekParity = "odd")
        assertTrue(c.matchesWeek(1))
        assertFalse(c.matchesWeek(2))
        assertTrue(c.matchesWeek(3))
        assertFalse(c.matchesWeek(4))
    }

    @Test
    fun `双周课只在偶数周`() {
        val c = course(startWeek = 1, endWeek = 16, weekParity = "even")
        assertFalse(c.matchesWeek(1))
        assertTrue(c.matchesWeek(2))
        assertFalse(c.matchesWeek(3))
        assertTrue(c.matchesWeek(4))
    }

    @Test
    fun `单双周要和区间同时满足`() {
        val c = course(startWeek = 5, endWeek = 9, weekParity = "odd")
        assertFalse(c.matchesWeek(3), "第 3 周在区间外")
        assertTrue(c.matchesWeek(5))
        assertFalse(c.matchesWeek(6), "偶数周不上")
        assertTrue(c.matchesWeek(7))
        assertTrue(c.matchesWeek(9))
        assertFalse(c.matchesWeek(11), "第 11 周在区间外")
    }

    @Test
    fun `中文单双周写法也认`() {
        assertTrue(course(weekParity = "单").matchesWeek(3))
        assertFalse(course(weekParity = "单").matchesWeek(4))
        assertTrue(course(weekParity = "双周").matchesWeek(4))
        assertFalse(course(weekParity = "双周").matchesWeek(5))
    }

    @Test
    fun `大小写和空格不影响判定`() {
        assertTrue(course(weekParity = " ODD ").matchesWeek(1))
        assertTrue(course(weekParity = "Even").matchesWeek(2))
    }

    @Test
    fun `没填周次时当作全学期每周都上——宁可多显示也不能让课消失`() {
        val c = course()
        assertTrue(c.matchesWeek(1))
        assertTrue(c.matchesWeek(10))
        assertTrue(c.matchesWeek(20))
    }

    @Test
    fun `周次字段缺省值覆盖 1 到 20 周`() {
        val c = Course(courseName = "X")
        assertTrue(c.matchesWeek(1))
        assertTrue(c.matchesWeek(20))
        assertFalse(c.matchesWeek(21))
    }

    @Test
    fun `未知的单双周字符串按每周都上处理，而不是全部过滤掉`() {
        // 模型偶尔会写 "all" / "每周" 之类，别因此把整门课弄没了
        assertTrue(course(weekParity = "weekly").matchesWeek(4))
        assertTrue(course(weekParity = "all").matchesWeek(5))
    }
}
