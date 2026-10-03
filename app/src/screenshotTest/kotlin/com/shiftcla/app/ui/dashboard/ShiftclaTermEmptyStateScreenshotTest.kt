package com.shiftcla.app.ui.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.shiftcla.app.data.TermPhase

/**
 * 学期前 / 学期后 两种空态的端到端视觉验收。
 *
 * 单测（[EmptyCopyTest]）钉的是**文案**，这里钉的是**UI 表现**——
 * 确认「学期还未开始」「学期已经过去」这两段话真的会以空态形式渲染出来，
 * 而不是被一屏旧课表盖住。
 *
 * 关键点：这两个空态**不看 `data.courses`**，只要 `emptyCopy` 是学期前/后，
 * 就该露出来（`ShiftclaApp` 内部是 `if (data.courses.isEmpty())` 才画空态，
 * 所以这里必须同时把 courses 清空 —— 对应真机上 `todayCoursesOf` 在学期外返回空）。
 */
class ShiftclaTermEmptyStateScreenshotTest {

    private fun emptyData() = ShiftclaSampleData.forScreenWidth(402).copy(courses = emptyList())

    /**
     * @param dateLabel 顶部日期。示例数据里是写死的「10月24日」，
     * 而真机上学期结束后打开 App 显示的是实际日期（寒假里的一月初/二月初），
     * 这里注入成对应的日期，让截图贴近真机观感。
     */
    @Composable
    private fun EmptyScreen(copy: EmptyCopy, dateLabel: String) {
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaApp(
                data = emptyData().copy(currentDate = dateLabel),
                animateOverlay = false,
                emptyCopy = copy,
            )
        }
    }

    /** 预备周（学期开始前）：日历图标 + 「学期还未开始」。 */
    @PreviewTest
    @Preview(name = "before-term", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun beforeTerm() {
        EmptyScreen(
            emptyCopyFor(TermPhase.BeforeTerm, hasAnyCourses = true, termNote = null),
            dateLabel = "9月10日 · 今天",
        )
    }

    /**
     * 学期结束后：星标图标 + 「学期已经过去了」。
     * 这里固定注入一句 AI 文案，避免截图依赖真机才有的 API Key / 网络。
     */
    @PreviewTest
    @Preview(name = "after-term", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun afterTerm() {
        EmptyScreen(
            emptyCopyFor(
                TermPhase.AfterTerm,
                hasAnyCourses = true,
                termNote = "考完了，先好好睡一觉，新学期课表出来记得导进来",
            ),
            dateLabel = "2月4日 · 今天",
        )
    }

    /** 学期后但 AI 文案还没回来（未配 Key / 请求中）：应退回兜底文案，不留空白。 */
    @PreviewTest
    @Preview(name = "after-term-fallback", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun afterTermFallback() {
        // termNote=null → emptyCopyFor 内部退回 TermNote.FALLBACK
        EmptyScreen(
            emptyCopyFor(TermPhase.AfterTerm, hasAnyCourses = true, termNote = null),
            dateLabel = "2月4日 · 今天",
        )
    }
}
