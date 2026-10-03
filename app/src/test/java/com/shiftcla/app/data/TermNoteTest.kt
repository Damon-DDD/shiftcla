package com.shiftcla.app.data

import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 学期结束那句 AI 文案的**提示词**测试。
 *
 * 这类断言看着土，但值得写：提示词拼错了不会有编译错误，
 * 线上表现只是"AI 答得莫名其妙"，靠肉眼极难定位。
 */
class TermNoteTest {

    private fun cal(y: Int, m: Int, d: Int) = Calendar.getInstance().apply {
        clear(); set(y, m - 1, d)
    }

    private fun promptOnHoliday() = TermNote.prompt(ShiftclaDate.snapshot(cal(2027, 2, 4)))

    @Test
    fun `提示词里带上今天日期、学期跨度、结束日期`() {
        val p = promptOnHoliday()
        assertTrue(p.contains("2027年2月4日"), "要告诉模型今天几号：$p")
        assertTrue(p.contains("第 1-17 教学周"), "要说清学期跨度：$p")
        assertTrue(p.contains("2027年1月10日"), "要说清哪天结束的：$p")
    }

    @Test
    fun `提示词同时要求 放松 和 提醒导入新数据`() {
        val p = promptOnHoliday()
        // 用户原话：「让 ai 写一点让用户去放松的话」+「让 ai 提示需要用户提供新的教务数据」
        assertTrue(p.contains("放松"), "少了【放松】这层意思：$p")
        assertTrue(p.contains("导入下个学期的课表"), "少了【提醒导入新数据】这层意思：$p")
        assertTrue(p.contains("40 字以内"), "不限长度模型会写小作文，标题下方放不下：$p")
    }

    @Test
    fun `不把内部字段名丢给模型`() {
        val p = promptOnHoliday()
        // promptContext 里带着 dayOfWeek=4 这种给解析引擎看的编号，
        // 写文案时没必要带上，免得模型照抄进句子里
        assertTrue(!p.contains("dayOfWeek"), "提示词里不该出现内部编号：$p")
    }

    @Test
    fun `兜底文案也得覆盖 放松 和 提醒 两层意思`() {
        assertTrue(TermNote.FALLBACK.contains("松"), "兜底文案少了放松：${TermNote.FALLBACK}")
        assertTrue(TermNote.FALLBACK.contains("课表"), "兜底文案少了提醒更新数据：${TermNote.FALLBACK}")
    }

    @Test
    fun `学期结束信息缺失时提示词也能拼完整`() {
        // 防御：万一以后 TERM_START 被清空，termEndLabel 会是 null，不能让提示词半句话断掉
        val broken = ShiftclaDate.snapshot(cal(2027, 2, 4)).copy(termEndLabel = null)
        val p = TermNote.prompt(broken)
        assertTrue(p.contains("已经结束了"), "termEndLabel 缺失时也要有完整句子：$p")
        assertTrue(!p.contains("null"), "别把 null 拼进提示词：$p")
    }
}
