package com.shiftcla.app.ui.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.shiftcla.app.data.ShiftclaDate
import java.util.Calendar

/**
 * **用户真实课表**的端到端视觉验收（2026-2027 秋季学期）。
 *
 * 为什么单开一组截图：上面 [RealScheduleTest] 验的是纯函数，
 * 这里验的是「数据真的能正确落到看板上」—— 也就是用户报的那个 bug：
 * *「它为啥老把别的周的课程加到本周来」*。
 *
 * 根因是 `ShiftclaDate` 原来把 9.7 当第 1 教学周（实际 9.7–9.13 是**预备周**），
 * 导致全学期周次**整体偏大 1 周**：App 以为自己在第 11 周，
 * 于是把「10-13周」的课显示出来、又把「2-9周」的课藏掉。
 *
 * 这组图把修复后的结果钉死：每个日期只该出现它当周真正要上的课。
 *
 * 后半段（`beforeTerm` / `afterTerm` / `afterTermFallback`）验的是**学期之外**的表现：
 * 预备周要说「学期还未开始」，学期结束后要说「学期已经过去了」并提醒导入新学期数据，
 * 两种情况都**不能再摆一屏已经过期/还没开始的课**。
 */
class ShiftclaRealScheduleScreenshotTest {

    private fun cal(y: Int, m: Int, d: Int) = Calendar.getInstance().apply {
        clear(); set(y, m - 1, d)
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

    private val tints = listOf(CardTint.Lavender, CardTint.Outlined, CardTint.Neutral)

    /** 上下午分区由开始时间决定（和 `DeepSeekEngine.inferBlock` 同一套规则）。 */
    private fun blockOf(time: String): CourseBlock {
        val hour = time.substringBefore(':').trim().toIntOrNull() ?: return CourseBlock.Morning
        return if (hour >= 12) CourseBlock.Afternoon else CourseBlock.Morning
    }

    /**
     * 复刻 `ShiftclaRoute` 的真实数据流：设备日期 → Snapshot → 按当天 + 当周筛课 → 喂给看板。
     * 唯一区别是日期由测试指定，这样截图才能稳定复现。
     */
    private fun realDashboard(y: Int, m: Int, d: Int): DashboardData {
        val snap = ShiftclaDate.snapshot(cal(y, m, d))
        val today = todayCoursesOf(mySchedule, snap)
        return DashboardData(
            currentDate = snap.currentDateLabel,
            weekLabel = snap.weekLabel,
            dateLabel = snap.dateLabel,
            weekdayLabel = snap.weekdayLabel,
            aiHeaderPrompt = if (today.isEmpty()) "今天没课，可以睡到自然醒" else "${today.size} 门课",
            courses = today.mapIndexed { i, course ->
                course.copy(
                    id = "r$i",
                    block = blockOf(course.startTime),
                    cardTint = tints[i % tints.size],
                )
            },
        )
    }

    /** 全量课表（带稳定 id + 样式），交给 Pager 自己按 selectedDate 过滤。 */
    private fun styledAllCourses(): List<Course> = mySchedule.mapIndexed { i, course ->
        course.copy(
            id = "r$i",
            block = blockOf(course.startTime),
            cardTint = tints[i % tints.size],
        )
    }

    @Composable
    private fun Screen(y: Int, m: Int, d: Int) {
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaDashboard(
                data = realDashboard(y, m, d),
                revealAnimation = false,
                // 切日体系：传全量课表 + 目标日期，Pager 页内自己按天过滤。
                // 不传的话 allCourses 默认取 data.courses（已过滤），会双重过滤导致空看板。
                allCourses = styledAllCourses(),
                selectedDate = java.time.LocalDate.of(y, m, d),
            )
        }
    }

    /**
     * 周四 · 第 3 教学周（奇数周）。
     * 周四共有 4 条记录，但：Python(2-9) ✓、计组周四(2-10 双周) ✗、马原周四(2-16 双周) ✗、
     * 实践课(14-17) ✗ → **只剩晚上那门 Python**。
     * 修复前这里会多出两门双周课。
     */
    @PreviewTest
    @Preview(name = "thu-week3", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun thursdayWeek3() = Screen(2026, 10, 1)

    /** 周二 · 第 2 教学周：三门全上，按 1-2 → 3-4 → 9-12 节排序。 */
    @PreviewTest
    @Preview(name = "tue-week2", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun tuesdayWeek2() = Screen(2026, 9, 22)

    /**
     * 周四 · 第 16 教学周（偶数周）：实践课开课 + 马原双周生效；
     * 计组周四只到第 10 周、Python 第 2-9 周 → 都已消失。
     * 修复前这个日期会算出"第 17 周"，把实践课（14-17）也一起漏掉。
     */
    @PreviewTest
    @Preview(name = "thu-week16", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun thursdayWeek16() = Screen(2026, 12, 31)

    /**
     * 学期**之前**（预备周内的周四 9.10）。
     *
     * 预期结果在这次改动中**反过来了**：以前是"周次未知就不按周过滤"，周四 4 条记录全显示；
     * 现在判定为 [TermPhase.BeforeTerm]，一条都不显示，界面给「学期还未开始」。
     * 理由：预备周压根没开课，列出来的都是"开学第一周才上"的安排。
     */
    @PreviewTest
    @Preview(name = "before-term", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun beforeTerm() = PhaseScreen(2026, 9, 10)

    /**
     * 学期**之后**（2027-02-04）。
     *
     * 显示「学期已经过去了」+ AI 写的那句话（放松 + 提醒导入新学期课表）。
     * 截图里没法真的调 API，所以钉一句写死的文案来验排版；
     * 真机上这句来自 `DeepSeekEngine.writeTermNote`，失败则退回 `TermNote.FALLBACK`。
     */
    @PreviewTest
    @Preview(name = "after-term", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun afterTerm() = PhaseScreen(
        y = 2027, m = 2, d = 4,
        termNote = "学期结束啦，先去吃顿好的 ☕ 新学期课表出来点左上角菜单导进来就行",
    )

    /** 兜底文案版：没配 API Key 或请求失败时的样子（不能是空白）。 */
    @PreviewTest
    @Preview(name = "after-term-fallback", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun afterTermFallback() = PhaseScreen(2027, 2, 4, termNote = null)

    /** 学期外的空态渲染：日期照旧来自设备时钟，只是课表不再显示。 */
    @Composable
    private fun PhaseScreen(y: Int, m: Int, d: Int, termNote: String? = null) {
        val snap = ShiftclaDate.snapshot(cal(y, m, d))
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaApp(
                data = realDashboard(y, m, d),
                animateOverlay = false,
                emptyCopy = emptyCopyFor(
                    phase = snap.phase,
                    hasAnyCourses = true,
                    termNote = termNote,
                ),
            )
        }
    }

    /** 同一份数据在平板宽度下的样子（分区标题 + 大屏建议卡）。 */
    @PreviewTest
    @Preview(name = "tue-week2-tablet", widthDp = 1280, heightDp = 800, showBackground = true)
    @Composable
    fun tuesdayWeek2Tablet() {
        MaterialTheme(typography = ShiftclaTypography) {
            val data = realDashboard(2026, 9, 22)
            ShiftclaDashboard(
                data = data.copy(aiInsightCard = "三门课都在经世楼和致用楼，课间不用跨楼跑 ✨"),
                revealAnimation = false,
            )
        }
    }
}
