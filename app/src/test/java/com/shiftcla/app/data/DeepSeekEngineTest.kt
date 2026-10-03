package com.shiftcla.app.data

import com.shiftcla.app.ui.dashboard.CourseBlock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DeepSeekEngine 的**容错解析**测试。
 *
 * 这一层不联网：把模型可能返回的各种"手抖格式"直接喂给 [DeepSeekEngine.extractJsonPayload]
 * 和 [DeepSeekEngine.parseContent]，验证清洗 + 反序列化链路。
 * 网络部分（Ktor 调用）没法在单测里验，但真正容易出错的就是解析。
 */
class DeepSeekEngineTest {

    /** 课程数组本体。 */
    private val goodArray = """
        [
          {
            "courseName": "线性代数",
            "teacher": "张明远 教授",
            "location": "教学楼 A-408",
            "dayOfWeek": 4,
            "startTime": "08:00",
            "endTime": "09:35",
            "tag": "测验",
            "aiNote": "今天有随堂测验，复习第六章行列式的性质"
          },
          {
            "courseName": "大学物理",
            "teacher": "赵启山 教授",
            "location": "理学楼 104",
            "dayOfWeek": 1,
            "startTime": "14:00",
            "endTime": "15:35",
            "tag": null,
            "aiNote": null
          }
        ]
    """.trimIndent()

    /** 新结构：顶层包裹对象。 */
    private val wrapped = """{"globalSummary":"今天两门硬课，别摸鱼","courses":$goodArray}"""

    // ---------------------------------------------------------------- 去壳

    @Test
    fun `纯 JSON 数组原样通过`() {
        val out = DeepSeekEngine.extractJsonPayload(goodArray)
        assertTrue(out.startsWith("["), "应以 [ 开头")
        assertTrue(out.endsWith("]"), "应以 ] 结尾")
    }

    @Test
    fun `剥掉 json 代码围栏`() {
        val raw = "```json\n$goodArray\n```"
        val courses = DeepSeekEngine.parseContent(raw)?.courses
        assertNotNull(courses, "带围栏的返回值应能解析")
        assertEquals(2, courses.size)
        assertEquals("线性代数", courses[0].courseName)
    }

    @Test
    fun `剥掉无语言标记的代码围栏`() {
        val raw = "```\n$goodArray\n```"
        assertEquals(2, DeepSeekEngine.parseContent(raw)?.courses?.size)
    }

    @Test
    fun `剥掉前置废话和后置说明`() {
        val raw = "好的，以下是解析结果：\n$goodArray\n以上就是全部课程，共 2 门。"
        val courses = DeepSeekEngine.parseContent(raw)?.courses
        assertNotNull(courses)
        assertEquals(2, courses.size)
        assertEquals("大学物理", courses[1].courseName)
    }

    @Test
    fun `围栏加废话一起上也能解`() {
        val raw = "已为你解析：\n```json\n$goodArray\n```\n还需要别的吗？"
        assertEquals(2, DeepSeekEngine.parseContent(raw)?.courses?.size)
    }

    // ---------------------------------------------------------------- 反序列化

    @Test
    fun `顶层 globalSummary 被解析出来`() {
        val r = assertNotNull(DeepSeekEngine.parseContent(wrapped))
        assertEquals("今天两门硬课，别摸鱼", r.globalSummary)
        assertEquals(2, r.courses.size)
    }

    @Test
    fun `globalSummary 缺失时为 null`() {
        val r = assertNotNull(DeepSeekEngine.parseContent("""{"courses":$goodArray}"""))
        assertNull(r.globalSummary)
        assertEquals(2, r.courses.size)
    }

    @Test
    fun `globalSummary 是空白串时归一化成 null`() {
        val r = assertNotNull(DeepSeekEngine.parseContent("""{"globalSummary":"   ","courses":[]}"""))
        assertNull(r.globalSummary)
    }

    @Test
    fun `只回裸数组也能解（模型没听 prompt 的兜底）`() {
        val r = assertNotNull(DeepSeekEngine.parseContent(goodArray))
        assertNull(r.globalSummary)
        assertEquals(2, r.courses.size)
    }

    @Test
    fun `只回 globalSummary 不给 courses 也不炸`() {
        val r = assertNotNull(DeepSeekEngine.parseContent("""{"globalSummary":"今天很轻松"}"""))
        assertTrue(r.courses.isEmpty(), "courses 缺省应是空列表而不是抛异常")
    }

    @Test
    fun `八个字段都被正确读出来`() {
        val c = DeepSeekEngine.parseContent(wrapped)!!.courses[0]
        assertEquals("线性代数", c.courseName)
        assertEquals("张明远 教授", c.teacher)
        assertEquals("教学楼 A-408", c.location)
        assertEquals(4, c.dayOfWeek)
        assertEquals("08:00", c.startTime)
        assertEquals("09:35", c.endTime)
        assertEquals("测验", c.tag)
        assertEquals("今天有随堂测验，复习第六章行列式的性质", c.aiNote)
    }

    @Test
    fun `缺字段不炸——缺失项走默认值`() {
        // 只给最少的几个字段
        val raw = """[{"courseName":"英语听说","startTime":"18:30","endTime":"20:05"}]"""
        val c = assertNotNull(DeepSeekEngine.parseContent(raw)?.courses)[0]
        assertEquals("英语听说", c.courseName)
        assertEquals("", c.location)
        assertEquals(1, c.dayOfWeek, "dayOfWeek 缺省应为 1")
        assertNull(c.tag)
        assertNull(c.aiNote)
    }

    @Test
    fun `多余字段被忽略而不是抛异常`() {
        val raw = """
            [{"courseName":"计算机网络","startTime":"15:45","endTime":"17:20",
              "credit":3,"classroomId":"C-101","nested":{"a":1},"days":[1,3]}]
        """.trimIndent()
        val courses = DeepSeekEngine.parseContent(raw)?.courses
        assertNotNull(courses, "未知键应被 ignoreUnknownKeys 吃掉")
        assertEquals("计算机网络", courses[0].courseName)
    }

    // ---------------------------------------------------------------- 补全派生字段

    @Test
    fun `id 缺失时按序号补上且唯一`() {
        val courses = DeepSeekEngine.parseContent(wrapped)!!.courses
        assertEquals(listOf("ds-0", "ds-1"), courses.map { it.id })
    }

    @Test
    fun `block 由开始时间推出——上午`() {
        assertEquals(CourseBlock.Morning, DeepSeekEngine.inferBlock("08:00"))
        assertEquals(CourseBlock.Morning, DeepSeekEngine.inferBlock("11:45"))
    }

    @Test
    fun `block 由开始时间推出——下午`() {
        assertEquals(CourseBlock.Afternoon, DeepSeekEngine.inferBlock("14:00"))
        assertEquals(CourseBlock.Afternoon, DeepSeekEngine.inferBlock("12:00"))
        assertEquals(CourseBlock.Afternoon, DeepSeekEngine.inferBlock("20:15"))
    }

    @Test
    fun `时间格式异常时保守归为上午`() {
        assertEquals(CourseBlock.Morning, DeepSeekEngine.inferBlock(""))
        assertEquals(CourseBlock.Morning, DeepSeekEngine.inferBlock("上午"))
    }

    // ---------------------------------------------------------------- 失败路径

    @Test
    fun `完全不是 JSON 时返回 null 而不是抛异常`() {
        assertNull(DeepSeekEngine.parseContent("抱歉，我无法解析这段文本。"))
    }

    @Test
    fun `空字符串返回 null`() {
        assertNull(DeepSeekEngine.parseContent(""))
    }

    @Test
    fun `JSON 结构对但类型不对时返回 null`() {
        // dayOfWeek 给了字符串、startTime 给了对象 —— 无法 coerce，应整体失败
        val raw = """[{"courseName":"X","startTime":{"h":8},"dayOfWeek":"周一"}]"""
        assertNull(DeepSeekEngine.parseContent(raw)?.courses)
    }

    @Test
    fun `空数组返回空列表而不是 null`() {
        val out = DeepSeekEngine.parseContent("[]")?.courses
        assertNotNull(out, "合法的空数组应当成功返回空列表")
        assertTrue(out.isEmpty())
    }

    // ---------------------------------------------------------------- 多模态 payload

    @Test
    fun `不带图时 content 是纯字符串`() {
        val c = DeepSeekEngine.buildUserContent("周一有课", emptyList())
        assertTrue(c is JsonPrimitive, "无图时 content 应是字符串，实际 $c")
        assertEquals("周一有课", (c as JsonPrimitive).content)
    }

    @Test
    fun `带图时 content 数组化成 Vision 格式`() {
        val c = DeepSeekEngine.buildUserContent("帮我看看这张课表", listOf("AAAABBBB"))
        assertTrue(c is JsonArray, "带图时 content 应是数组")
        val arr = c as JsonArray
        assertEquals(2, arr.size, "应当是 [text, image_url] 两段")
        // 第一段是文本、第二段是图 —— 顺序不能反，模型先看任务描述
        assertTrue(arr[0].toString().contains("\"type\":\"text\""), "第 0 段应是 text：${arr[0]}")
        val img = arr[1].toString()
        assertTrue(img.contains("\"type\":\"image_url\""), "第 1 段应是 image_url：$img")
        assertTrue(img.contains("data:image/jpeg;base64,AAAABBBB"), "URL 应为 data URL：$img")
    }

    @Test
    fun `图片 base64 为空串时退回纯文本`() {
        val c = DeepSeekEngine.buildUserContent("只有文字", listOf(""))
        assertTrue(c is JsonPrimitive, "空 base64 应等同没选图")
    }

    @Test
    fun `多张图时每张各占一段`() {
        val c = DeepSeekEngine.buildUserContent("三张图", listOf("IMG1", "IMG2", "IMG3"))
        val arr = c as JsonArray
        // 1 段文本 + 3 段图片
        assertEquals(4, arr.size, "应是 [text, img, img, img]，实际 $arr")
        assertTrue(arr[0].toString().contains("\"type\":\"text\""))
        for (i in 1..3) {
            assertTrue(
                arr[i].toString().contains("data:image/jpeg;base64,IMG$i"),
                "第 $i 段应带第 $i 张图：${arr[i]}",
            )
        }
    }

    @Test
    fun `多张图里的空串被丢掉、不影响其余`() {
        val c = DeepSeekEngine.buildUserContent("混了空串", listOf("IMG1", "", "IMG2"))
        assertEquals(3, (c as JsonArray).size, "空串不该占一段")
    }

    // ------------------------------------------------------------ 季节性文案清洗

    /**
     * 让 AI 写"学期结束了"那句话时，它总会加引号、换行、或者来段 Markdown。
     * 这些符号直接渲染到界面上会很难看，所以必须清洗。
     *
     * 网络那一半没法单测，但清洗这一半全是分支，正好该测。
     */
    @Test
    fun `AI 写的句子被去掉成对引号`() {
        assertEquals("学期结束啦", DeepSeekEngine.sanitizeNote("\"学期结束啦\""))
        assertEquals("学期结束啦", DeepSeekEngine.sanitizeNote("“学期结束啦”"))
        assertEquals("学期结束啦", DeepSeekEngine.sanitizeNote("「学期结束啦」"))
        assertEquals("学期结束啦", DeepSeekEngine.sanitizeNote("'学期结束啦'"))
    }

    @Test
    fun `AI 写的句子被去掉 Markdown 强调符号`() {
        assertEquals("去放松一下", DeepSeekEngine.sanitizeNote("**去放松一下**"))
        assertEquals("去放松一下", DeepSeekEngine.sanitizeNote("*去放松一下*"))
    }

    @Test
    fun `多行被压成一行、首尾空白被吃掉`() {
        assertEquals("学期结束啦 好好休息", DeepSeekEngine.sanitizeNote("  学期结束啦\n\n  好好休息  \n"))
    }

    @Test
    fun `清洗不会误伤正文里的标点`() {
        // 冒号、顿号、emoji、书名号都不该被动
        val raw = "考完啦！先去吃顿好的，记得导入《新学期课表》☕"
        assertEquals(raw, DeepSeekEngine.sanitizeNote(raw))
    }

    @Test
    fun `空响应清洗后仍是空串——由调用方判空决定要不要退回兜底`() {
        assertEquals("", DeepSeekEngine.sanitizeNote(""))
        assertEquals("", DeepSeekEngine.sanitizeNote("   \n  "))
        assertEquals("", DeepSeekEngine.sanitizeNote("\"\""))
    }

    // ------------------------------------------------------------ 提示词契约

    /**
     * 提示词里的**字段清单**必须涵盖周次三件套。
     *
     * 踩过的坑：`【courses】每个元素必须包含` 那行原来只列了 8 个字段，
     * 没有 startWeek / endWeek / weekParity。模型严格照清单填 → 周次全走默认值 1..20
     * → 每门课都"每周都上" → 用户看到的现象就是「别的周的课老被加到本周来」。
     *
     * 这条断言很土，但它锁的是一个**只靠读代码发现不了、只有线上表现才暴露**的契约。
     */
    @Test
    fun `提示词的字段清单必须包含周次三件套`() {
        val prompt = DeepSeekEngine.SYSTEM_PROMPT
        for (field in listOf("startWeek", "endWeek", "weekParity")) {
            assertTrue(prompt.contains(field), "字段清单漏了 $field —— 模型会漏填，整门课退化成每周都上")
        }
        // 出现在"必须包含"那句之后，才算被列进了清单（而不是只在规则正文里被提一句）
        val listLine = prompt.substringAfter("必须包含").substringBefore("\n")
        for (field in listOf("startWeek", "endWeek", "weekParity")) {
            assertTrue(listLine.contains(field), "「必须包含」清单里没有 $field：$listLine")
        }
    }

    /** 周次规则得把「不要按周筛选」写清楚，否则模型会自作主张只返回本周的课。 */
    @Test
    fun `提示词要求返回全部课程而不是只返回本周`() {
        val prompt = DeepSeekEngine.SYSTEM_PROMPT
        assertTrue(prompt.contains("全部课程"), "必须要求返回输入里的全部课程")
        assertTrue(prompt.contains("不要自己按周次") || prompt.contains("不要按周"), "要明确禁止模型自己按周筛选")
        assertTrue(prompt.contains("同一门课跨多周只输出一条"), "要禁止把一门课按周拆成多条")
    }
}
