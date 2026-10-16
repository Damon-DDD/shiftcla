@file:Suppress("SpellCheckingInspection")

package com.shiftcla.app.ui.dashboard

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
 * 当前该显示**哪一种**空态。
 *
 * 这里只判分支、不管文案 —— 文案在 strings.xml（按语言切换），
 * 分支逻辑留在这层纯函数里，单测断言的是"哪个场景对应哪种空态"，
 * 而不是某句中文，改文案时测试不会误报。
 */
enum class EmptyCase {
    /** 学期还没开始（含预备周） */
    BeforeTerm,

    /** 学期已结束 */
    AfterTerm,

    /** 学期中，但**今天**没课 */
    NoClassToday,

    /** 学期中，切到了别的日子且那天没课 */
    NoClassThatDay,

    /** 还没导入过课表 */
    NoSchedule,
}

/**
 * 按**学期阶段** + 有没有课表 + 是不是今天，决定显示哪种空态。
 *
 * 抽成纯函数有两个好处：
 *  1. 分支多（3 个阶段 × 有没有课表 × 是不是今天），散在 Composable 里的 `if` 嵌套基本没法测；
 *  2. 「学期前/学期后」这两种文案是用户明确指定的，值得用断言钉住，别被后人顺手改掉。
 *
 * @param isToday 选中的日期是不是真实今天。左右滑 / 日历切到别的日子后，
 *                「今天没有课」要变成「这天没有课」—— 否则文案在撒谎。
 */
internal fun emptyCaseFor(
    phase: TermPhase,
    hasAnyCourses: Boolean,
    isToday: Boolean = true,
): EmptyCase = when (phase) {
    // 预备周及更早：还没开课，别把"下周才上"的安排列出来吓人
    TermPhase.BeforeTerm -> EmptyCase.BeforeTerm

    // 学期结束：课表已过期，重点是"歇一歇 + 该更新数据了"
    TermPhase.AfterTerm -> EmptyCase.AfterTerm

    // 学期中：这两种空态的含义完全不同，必须分开
    TermPhase.InTerm -> if (hasAnyCourses) {
        if (isToday) EmptyCase.NoClassToday else EmptyCase.NoClassThatDay
    } else {
        EmptyCase.NoSchedule
    }
}
