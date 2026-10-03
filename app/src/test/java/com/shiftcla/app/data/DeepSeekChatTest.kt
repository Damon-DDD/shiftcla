package com.shiftcla.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 多轮对话的**消息组装**测试。
 *
 * 网络发不出去，但"喂给模型什么"是完全确定的，也正是"能不能改课表"的关键：
 * 不带「当前课表」上下文，模型每轮只能从零重新解析用户那句话，改不了已有的东西。
 */
class DeepSeekChatTest {

    private fun sysText(m: ChatMessage) = (m.content as JsonPrimitive).content

    @Test
    fun `首轮只有一条 system，不带当前课表也不带改名规则`() {
        val msgs = DeepSeekEngine.buildMessages(
            history = listOf(ChatTurn("user", "周一 8 点高数")),
            currentScheduleJson = null,
        )
        assertEquals(2, msgs.size, "首轮应是 [system, user]")
        assertEquals("system", msgs[0].role)
        assertEquals("user", msgs[1].role)
        assertTrue(
            !sysText(msgs[0]).contains("多轮修改"),
            "首轮不该出现多轮修改规则",
        )
        // 日期上下文始终要在
        assertTrue(sysText(msgs[0]).contains("dayOfWeek="), "系统提示应带当天日期上下文")
    }

    @Test
    fun `后续轮会带上当前课表——这是能改课表的前提`() {
        val current = """{"globalSummary":"x","courses":[]}"""
        val msgs = DeepSeekEngine.buildMessages(
            history = listOf(
                ChatTurn("user", "周一 8 点高数"),
                ChatTurn("assistant", current),
                ChatTurn("user", "把教室改成 B-302"),
            ),
            currentScheduleJson = current,
        )
        // [system, system(课表), user, assistant, user]
        assertEquals(5, msgs.size, "实际：${msgs.map { it.role }}")
        assertEquals("system", msgs[1].role)
        assertTrue(sysText(msgs[1]).contains(current), "第 2 条 system 必须是当前课表原文")
        assertTrue(sysText(msgs[0]).contains("多轮修改"), "后续轮应带上修改规则")
    }

    @Test
    fun `当前课表上下文排在历史之前——先建立基准再读指令`() {
        val msgs = DeepSeekEngine.buildMessages(
            history = listOf(ChatTurn("user", "删掉周三的课")),
            currentScheduleJson = """{"courses":[]}""",
        )
        val scheduleIdx = msgs.indexOfFirst { sysText(it).contains("当前课表（JSON）") }
        val userIdx = msgs.indexOfFirst { it.role == "user" }
        assertTrue(scheduleIdx in 1..<userIdx, "课表上下文应在 user 之前，实际 $scheduleIdx vs $userIdx")
    }

    @Test
    fun `对话顺序原样保留`() {
        val msgs = DeepSeekEngine.buildMessages(
            history = listOf(
                ChatTurn("user", "第一句"),
                ChatTurn("assistant", "{...}"),
                ChatTurn("user", "第二句"),
            ),
        )
        assertEquals(
            listOf("system", "user", "assistant", "user"),
            msgs.map { it.role },
        )
    }

    @Test
    fun `assistant 轮原样当字符串传，不重新解析`() {
        val json = """{"globalSummary":"g","courses":[]}"""
        val msgs = DeepSeekEngine.buildMessages(
            history = listOf(ChatTurn("user", "a"), ChatTurn("assistant", json), ChatTurn("user", "b")),
        )
        val assistant = msgs.first { it.role == "assistant" }
        assertTrue(assistant.content is JsonPrimitive, "assistant 的 content 应是纯字符串")
        assertEquals(json, (assistant.content as JsonPrimitive).content)
    }

    @Test
    fun `图片只挂在最后一条 user 上`() {
        val msgs = DeepSeekEngine.buildMessages(
            history = listOf(ChatTurn("user", "第一句"), ChatTurn("user", "第二句带图")),
            images = listOf("IMG"),
        )
        assertTrue(msgs[1].content is JsonPrimitive, "第一句不该带图")
        val last = msgs[2].content
        assertTrue(last is JsonArray, "最后一句应数组化以承载图片")
        assertTrue(last.toString().contains("data:image/jpeg;base64,IMG"))
    }

    // ---------------------------------------------------------------- 课表上下文往返

    @Test
    fun `课表序列化后能原样解回来——否则第二轮就丢数据`() {
        val original = ScheduleResponse(
            globalSummary = "今天两门硬课",
            courses = listOf(
                com.shiftcla.app.ui.dashboard.Course(
                    id = "c1", courseName = "线性代数", teacher = "张明远",
                    location = "A-408", dayOfWeek = 4,
                    startTime = "08:00", endTime = "09:35", tag = "测验",
                ),
            ),
        )
        val json = DeepSeekEngine.encodeSchedule(original)
        val back = assertNotNullish(DeepSeekEngine.parseContent(json))
        assertEquals("今天两门硬课", back.globalSummary)
        assertEquals(1, back.courses.size)
        assertEquals("线性代数", back.courses[0].courseName)
        assertEquals(4, back.courses[0].dayOfWeek, "dayOfWeek 必须活着往返")
        assertEquals("08:00", back.courses[0].startTime)
    }

    private fun <T> assertNotNullish(v: T?): T {
        assertTrue(v != null, "不应为 null")
        return v!!
    }

    // ---------------------------------------------------------------- 持久化往返

    /**
     * [ScheduleStore] 靠 encode→decode 做持久化，所以这条要保证「存进磁盘再读回来」
     * 时**周次三件套 + termStartDate** 一个都不丢 ——
     * 否则修完「后台划掉不丢课表」后，冷启动恢复出的课表周次信息缺失，
     * 又会退回 `1..20` 的默认值，等于"别的周的课又混进本周"。
     */
    @Test
    fun `持久化往返不丢周次三件套和学期起点`() {
        val original = ScheduleResponse(
            globalSummary = "三门课",
            termStartDate = "2026-09-14",
            courses = listOf(
                com.shiftcla.app.ui.dashboard.Course(
                    id = "c1", courseName = "计算机组成原理B", location = "致用楼-210",
                    dayOfWeek = 4, startTime = "08:10", endTime = "09:50",
                    startWeek = 2, endWeek = 10, weekParity = "even",
                ),
                com.shiftcla.app.ui.dashboard.Course(
                    id = "c2", courseName = "程序设计综合实践B", location = "经世楼-717",
                    dayOfWeek = 1, startTime = "08:10", endTime = "11:50",
                    startWeek = 14, endWeek = 17,
                ),
            ),
        )
        val json = DeepSeekEngine.encodeSchedule(original)
        val back = assertNotNullish(DeepSeekEngine.parseContent(json))

        assertEquals("2026-09-14", back.termStartDate, "学期起点必须在持久化里活着往返")
        assertEquals(2, back.courses.size)

        val c1 = back.courses[0]
        assertEquals(2, c1.startWeek)
        assertEquals(10, c1.endWeek)
        assertEquals("even", c1.weekParity, "单双周标记不能丢")
        // 反序列化后 matchesWeek 仍要能正确判：第 10 周（偶数）该上，第 9 周（奇数）不上
        assertTrue(c1.matchesWeek(10), "双周课在第 10 周该上")
        assertTrue(!c1.matchesWeek(9), "双周课在第 9 周不该上")

        val c2 = back.courses[1]
        assertEquals(14, c2.startWeek)
        assertEquals(17, c2.endWeek)
        assertEquals(null, c2.weekParity, "没有单双周标记应保持 null 而不是变成别的")
    }
}
