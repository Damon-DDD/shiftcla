@file:Suppress("SpellCheckingInspection")

package com.shiftcla.app.ui.dashboard

import com.shiftcla.app.data.ShiftclaDate
import com.shiftcla.app.data.TermPhase

/**
 * 从整份课表里挑出「今天该显示哪些课」：**按星期 + 教学周过滤 → 去重 → 按开始时间升序**。
 *
 * 抽成**顶层纯函数**（而不是 `ShiftclaViewModel` 的成员）有两个原因：
 *
 * 1. 它本身就是"列表 → 列表"的纯计算，不依赖任何状态；
 * 2. 做成 ViewModel 成员的话，单测里一写 `ShiftclaViewModel()` 就会初始化
 *    `viewModelScope`（内部是 `Dispatchers.Main.immediate`），而 JVM 单测没有 Main
 *    调度器，直接抛 `Module with the Main dispatcher had failed to initialize`。
 *    纯函数 + 显式传入 [ShiftclaDate.Snapshot]，测试就与系统时钟、与 Android 主线程全都解耦。
 */
internal fun todayCoursesOf(
    all: List<Course>,
    today: ShiftclaDate.Snapshot,
): List<Course> {
    // 学期之外一律空空如也：
    //  · 预备周还没开课，列出来的都是"下周才上"的安排；
    //  · 学期结束后整份课表都已过期，继续显示会让人以为还在生效。
    // 返回空列表同时是**让空态文案露出来的前提** —— UI 那边是
    // `if (data.courses.isEmpty())` 才画提示，不返回空就会被一屏旧课盖住。
    if (today.phase != TermPhase.InTerm) return emptyList()

    return all.filter { course ->
        course.dayOfWeek == today.dayOfWeek &&
                // 教学周也要对：课表里满是「1-16周」「单周」，不能一股脑全塞进本周。
                // weekNumber 在教学周内必定非 null；这里保留 null 分支只是为了防御。
                (today.weekNumber?.let { course.matchesWeek(it) } ?: true)
    }
        // 去重键必须带 dayOfWeek：同一门课「周一 1-4 节 @519」和「周五 1-4 节 @519」
        // 时间与地点可能完全一样，只有星期不同 —— 漏了它会把其中一条误删。
        .distinctBy { "${it.dayOfWeek}|${it.startTime}|${it.courseName}|${it.location}" }
        .sortedBy { ShiftclaDate.minutesOf(it.startTime) }
}
