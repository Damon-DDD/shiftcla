@file:Suppress("SpellCheckingInspection")

package com.shiftcla.app.ui.dashboard

import com.shiftcla.app.data.TermNote
import com.shiftcla.app.data.TermPhase

/** 空态用哪个图标（放纯逻辑文件里，避免把 Compose 类型拖进单测）。 */
enum class EmptyKind { Sparkle, Calendar }

/**
 * 空态文案。
 *
 * @param title 主标题
 * @param body  副文案，可以含 `\n`
 */
data class EmptyCopy(
    val title: String,
    val body: String,
    val kind: EmptyKind,
)

/**
 * 按**学期阶段** + 有没有课表，决定空态说什么。
 *
 * 抽成纯函数有两个好处：
 *  1. 分支多（3 个阶段 × 有没有课表 × 是不是今天），散在 Composable 里的 `if` 嵌套基本没法测；
 *  2. 「学期前/学期后」这两种文案是用户明确指定的，值得用断言钉住，别被后人顺手改掉。
 *
 * @param termNote 学期结束后 AI 写的那句话；null 时退回 [TermNote.FALLBACK]
 * @param isToday  选中的日期是不是真实今天。左右滑 / 日历切到别的日子后，
 *                 「今天没有课」要变成「这天没有课」—— 否则文案在撒谎。
 */
internal fun emptyCopyFor(
    phase: TermPhase,
    hasAnyCourses: Boolean,
    termNote: String?,
    isToday: Boolean = true,
): EmptyCopy = when (phase) {
    // 预备周及更早：还没开课，别把"下周才上"的安排列出来吓人
    TermPhase.BeforeTerm -> EmptyCopy(
        title = "学期还未开始",
        body = "新学期还没开课\n先把教务数据导进来，开学第一天它就是现成的",
        kind = EmptyKind.Calendar,
    )

    // 学期结束：课表已过期，重点是"歇一歇 + 该更新数据了"
    TermPhase.AfterTerm -> EmptyCopy(
        title = "学期已经过去了",
        body = termNote?.takeIf { it.isNotBlank() } ?: TermNote.FALLBACK,
        kind = EmptyKind.Sparkle,
    )

    // 学期中：这两种空态的含义完全不同，必须分开
    TermPhase.InTerm -> if (hasAnyCourses) {
        EmptyCopy(
            title = if (isToday) "今天没有课" else "这天没有课",
            body = if (isToday) "今天没有排课，好好休息"
            else "这天没有排课\n点「回到当天课程」回到今天，或换个日期看看",
            kind = EmptyKind.Sparkle,
        )
    } else {
        EmptyCopy(
            title = "还没有课表",
            body = "点左上角菜单 → 导入教务数据\n把教务文本丢给 AI，它会帮你排好",
            kind = EmptyKind.Sparkle,
        )
    }
}
