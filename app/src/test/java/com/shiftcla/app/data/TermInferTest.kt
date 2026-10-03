package com.shiftcla.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 学期信息「自动推断」的单元测试。
 *
 * 之前学期起点 / 周数是硬编码的（`TERM_START = 2026-09-14`、`TERM_WEEKS = 17`），
 * 换学期要改代码。现在改成：AI 解析课表时返回 `termStartDate`，周数从课程 `endWeek`
 * 最大值反推，一起存进 [TermInfoStore]。这组测试钉住三件事：
 *
 * 1. [ShiftclaDate.parseIsoDate] 的容错 —— 它决定了「AI 给的值能不能信」；
 * 2. `termStartDate` 能进得了 `ScheduleResponse` 并透过 `decodeSchedule` 保留；
 * 3. 提示词确实要求模型返回 `termStartDate`（否则模型根本不填）。
 */
class TermInferTest {

    // ------------------------------------------------------------ parseIsoDate

    @Test
    fun `合法 ISO 日期能解析`() {
        val c = ShiftclaDate.parseIsoDate("2026-09-14")
        assertNotNull(c)
        assertEquals(2026, c.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.SEPTEMBER, c.get(java.util.Calendar.MONTH))
        assertEquals(14, c.get(java.util.Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `非法日期返回 null——越界、非数字、格式错`() {
        assertNull(ShiftclaDate.parseIsoDate("2026-02-31"), "2月没有31号")
        assertNull(ShiftclaDate.parseIsoDate("2026-13-01"), "13月不存在")
        assertNull(ShiftclaDate.parseIsoDate("2026-00-10"), "0月不存在")
        assertNull(ShiftclaDate.parseIsoDate("2026-04-00"), "0号不存在")
        assertNull(ShiftclaDate.parseIsoDate("不是日期"))
        assertNull(ShiftclaDate.parseIsoDate(""))
        assertNull(ShiftclaDate.parseIsoDate("2026/09/14"), "斜杠不算")
        assertNull(ShiftclaDate.parseIsoDate("2026-09"), "缺日")
    }

    @Test
    fun `月份不补零也宽容解析`() {
        // AI 偶尔给 "2026-9-14"（月不补零），split 后照样是数字，应当放行而不是拒掉
        val c = ShiftclaDate.parseIsoDate("2026-9-14")
        assertNotNull(c, "月不补零是合法日期，宽容处理")
        assertEquals(14, c.get(java.util.Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `前后带空格也能解析`() {
        // AI 常给 " 2026-09-14 "，decodeSchedule 会 trim，这里再确认 parse 也宽容
        assertNotNull(ShiftclaDate.parseIsoDate(" 2026-09-14 "))
    }

    @Test
    fun `闰年 2 月 29 合法、平年 2 月 29 非法`() {
        assertNotNull(ShiftclaDate.parseIsoDate("2024-02-29"), "2024 是闰年")
        assertNull(ShiftclaDate.parseIsoDate("2026-02-29"), "2026 不是闰年")
    }

    // ------------------------------------------------------ termStartDate 进响应

    @Test
    fun `decodeSchedule 保留并清洗 termStartDate`() {
        val json = """
            {"globalSummary":"一周四天","termStartDate":" 2026-09-14 ",
             "courses":[{"courseName":"高等数学","dayOfWeek":1,"startTime":"08:00","endTime":"09:35","startWeek":1,"endWeek":16}]}
        """.trimIndent()
        val r = DeepSeekEngine.decodeSchedule(json)
        assertEquals("2026-09-14", r.termStartDate, "应 trim 掉首尾空格")
    }

    @Test
    fun `termStartDate 缺失时为 null 且不影响课程解析`() {
        val json = """
            {"globalSummary":"x","courses":[{"courseName":"大学英语","dayOfWeek":3,"startTime":"10:00","endTime":"11:35"}]}
        """.trimIndent()
        val r = DeepSeekEngine.decodeSchedule(json)
        assertNull(r.termStartDate)
        assertEquals(1, r.courses.size)
    }

    // ------------------------------------------------------------ 提示词契约

    @Test
    fun `提示词要求模型返回 termStartDate`() {
        val p = DeepSeekEngine.SYSTEM_PROMPT
        assertTrue(p.contains("termStartDate"), "顶层结构里必须提到 termStartDate")
        assertTrue(p.contains("YYYY-MM-DD"), "要明确日期格式")
        // 关键：禁止模型瞎编日期，否则推断出的学期起点是错的
        assertTrue(p.contains("不要编造") || p.contains("别编造") || p.contains("编造"), "要禁止编造开学日期")
        // 预备周不算第 1 周，这个坑踩过
        assertTrue(p.contains("预备周"), "要提醒模型预备周不是第 1 教学周")
    }

    @Test
    fun `提示词字段清单仍包含周次三件套`() {
        // 加了 termStartDate 之后，别把 courses 的字段清单挤坏
        val listLine = DeepSeekEngine.SYSTEM_PROMPT
            .substringAfter("必须包含").substringBefore("\n")
        for (f in listOf("startWeek", "endWeek", "weekParity")) {
            assertTrue(listLine.contains(f), "字段清单漏了 $f：$listLine")
        }
    }
}
