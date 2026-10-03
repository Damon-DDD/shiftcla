package com.shiftcla.app.data

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 日期切换体系（左右滑 / 日历）的纯逻辑测试。
 *
 * 覆盖三块最容易算错、且肉眼看代码一定会漏的：
 *  1. [ShiftclaDate.monthGrid] —— 周一开头、固定 42 格、跨月补位；
 *  2. [ShiftclaDate.dateSuffix] / [currentDateLabel] —— 今天/昨天/明天/周X 的文案；
 *  3. Pager 页码 ↔ 日期 的映射（映射函数在 `ui/dashboard/ShiftclaDashboard.kt` 顶层）。
 */
class DateSwitchTest {

    // ------------------------------------------------------------ 月份网格

    @Test
    fun `月份网格固定 42 格且周一开头`() {
        // 2026-10：10月1日是周四。周一开头 → 首个格子应是 9月28日（周一）
        val grid = ShiftclaDate.monthGrid(YearMonth.of(2026, 10))
        assertEquals(42, grid.size)
        assertEquals(LocalDate.of(2026, 9, 28), grid.first(), "首个格子应是该月第一周前的周一")
        // 第 1 个格子的 dayOfWeek 必须是周一（value 1）
        assertEquals(1, grid.first().dayOfWeek.value)
        // 最后一个格子 = 首个 + 41 天
        assertEquals(grid.first().plusDays(41), grid.last())
    }

    @Test
    fun `月份网格包含该月所有日期`() {
        val grid = ShiftclaDate.monthGrid(YearMonth.of(2026, 10))
        assertTrue(LocalDate.of(2026, 10, 1) in grid)
        assertTrue(LocalDate.of(2026, 10, 31) in grid)
    }

    @Test
    fun `月初是周一月份网格从 1 号开始`() {
        // 2026-06-01 是周一
        val grid = ShiftclaDate.monthGrid(YearMonth.of(2026, 6))
        assertEquals(LocalDate.of(2026, 6, 1), grid.first())
    }

    // ------------------------------------------------------------ 日期文案

    @Test
    fun `日期后缀今天昨天明天周X`() {
        val today = LocalDate.of(2026, 10, 1)
        assertEquals("今天", ShiftclaDate.dateSuffix(today, today))
        assertEquals("昨天", ShiftclaDate.dateSuffix(today.minusDays(1), today))
        assertEquals("明天", ShiftclaDate.dateSuffix(today.plusDays(1), today))
        // 10.5 是周一（2026）
        assertEquals("周一", ShiftclaDate.dateSuffix(LocalDate.of(2026, 10, 5), today))
    }

    @Test
    fun `手机端日期文案带今天标记`() {
        val today = LocalDate.of(2026, 10, 1)
        assertEquals("10月1日 · 今天", ShiftclaDate.currentDateLabel(today, today))
        assertEquals("10月2日 · 明天", ShiftclaDate.currentDateLabel(today.plusDays(1), today))
    }

    // ------------------------------------------------------------ Pager 映射

    @Test
    fun `页码与日期互为逆映射`() {
        val today = LocalDate.of(2026, 10, 1)
        // pageToDate / dateToPage 在 `ui.dashboard` 包，这里直接引用顶层函数
        val page = com.shiftcla.app.ui.dashboard.dateToPage(today, today)
        assertEquals(com.shiftcla.app.ui.dashboard.pageToDate(page, today), today)

        val d = LocalDate.of(2026, 10, 5)
        val p = com.shiftcla.app.ui.dashboard.dateToPage(d, today)
        assertEquals(d, com.shiftcla.app.ui.dashboard.pageToDate(p, today), "往返应无损")
    }

    @Test
    fun `相邻页相差一天`() {
        val today = LocalDate.of(2026, 10, 1)
        val p = com.shiftcla.app.ui.dashboard.dateToPage(today, today)
        assertEquals(
            today.plusDays(1),
            com.shiftcla.app.ui.dashboard.pageToDate(p + 1, today),
            "向右一页 = 明天",
        )
        assertEquals(
            today.minusDays(1),
            com.shiftcla.app.ui.dashboard.pageToDate(p - 1, today),
            "向左一页 = 昨天",
        )
    }
}
