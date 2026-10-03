@file:Suppress("SpellCheckingInspection")

package com.shiftcla.app.data

import java.time.LocalDate
import java.time.YearMonth
import java.util.Calendar
import java.util.Locale

/**
 * 相对当前学期的三个阶段。
 *
 * 存在的意义：**看板在学期外不该继续显示上学期的旧课**。
 * 之前只有"有课/没课"两种空态，寒暑假打开 App 会看到一屏早已结课的安排，
 * 既不准确也容易让人误以为课表还在生效。
 */
enum class TermPhase {
    /** 学期还没开始（含开学前的**预备周**）。 */
    BeforeTerm,

    /** 处在 1..[ShiftclaDate.TERM_WEEKS] 教学周之内。 */
    InTerm,

    /** 学期已经结束。 */
    AfterTerm,
}

/**
 * 日期/时间同步。
 *
 * 之前看板上的 `"10月24日 · 今天"` 是设计稿里抄死的字符串，装到手机上永远是那天。
 * 这里统一从设备时钟算，并给 AI 的提示词提供日期上下文（否则"明天有课吗"这类
 * 相对日期它没法解析）。
 *
 * **为什么用 `java.util.Calendar` 而不是 `java.time`**：`java.time` 在 minSdk < 26
 * 上要么开 core library desugaring、要么做兼容判断；本项目 minSdk 是 24，
 * 而这里只需要"今天是几号/周几/第几周"，`Calendar` 足够且零依赖。
 */
object ShiftclaDate {

    /**
     * **第 1 教学周的周一**。周次（"Week 10"）和"按当前教学周筛选"都靠它反推。
     *
     * ⚠️ 别把"预备周"当成第 1 周。以 2026-2027 秋季学期为例，校历是：
     *   预备周 9.7–9.13（不算教学周） → 第 1 周 9.14–9.20 → 第 2 周 9.21–9.27 …
     * 我第一版填了 9.7，结果全学期周次**整体偏大 1 周**。
     *
     * 📌 这已经**不再是单一事实源**了：它只是「用户还没导入过课表」时的兜底默认值。
     * 一旦用户在课表里导入成功，[TermInfoStore] 会存下真实学期（起点日期 + 周数），
     * [termStart] 优先返回那个值。见 [termStart] / [termWeeks]。
     */
    private val DEFAULT_TERM_START: Calendar? = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.SEPTEMBER, 14)   // 2026-09-14（周一）= 第 1 教学周
    }

    /**
     * 本学期一共几个教学周。
     *
     * 17 是**从课表数据里量出来的**：最晚结课的是「程序设计综合实践B · 第 14-17 周」。
     * 所以学期覆盖 第 1..17 周 = 2026-09-14 ~ 2027-01-10。
     *
     * 和 [DEFAULT_TERM_START] 一样，只是「没导入过课表」时的兜底值；
     * 真实周数由 [termWeeks] 优先读 [TermInfoStore]。
     */
    const val TERM_WEEKS: Int = 17

    private val MONTH_ABBR = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
    private val WEEKDAY_CN = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private val WEEKDAY_EN = arrayOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
    )

    /** 一次算好，避免在组合里反复取时钟。 */
    data class Snapshot(
        /** 1 = 周一 … 7 = 周日，和 [com.shiftcla.app.ui.dashboard.Course.dayOfWeek] 同一套编号 */
        val dayOfWeek: Int,
        /** 手机端右上角："10月1日 · 今天" */
        val currentDateLabel: String,
        /** 平板端日期胶囊左半："Oct 1"（设计稿就是英文缩写） */
        val dateLabel: String,
        /** 平板端日期胶囊右半："Thursday" */
        val weekdayLabel: String,
        /** 当前教学周序号（1 起）；**只在 [TermPhase.InTerm] 时有值** */
        val weekNumber: Int?,
        /** 周次胶囊："Week 4"；非学期中为 null */
        val weekLabel: String?,
        /** 处在学期的哪个阶段 */
        val phase: TermPhase,
        /** 学期最后一天："2027年1月10日"；起点未知时为 null */
        val termEndLabel: String?,
        /** 给提示词用的一句话："今天是 2026年10月1日，周四（dayOfWeek=4），现在是第 3 教学周" */
        val promptContext: String,
        /**
         * 学期标识，用来给"AI 写的季节性文案"做缓存键 ——
         * 同一个学期只让 AI 写一次，换学期自动失效重写。
         */
        val termKey: String,
    )

    fun snapshot(now: Calendar = Calendar.getInstance()): Snapshot {
        val dow = dayOfWeekOf(now)
        val month = now.get(Calendar.MONTH) + 1
        val day = now.get(Calendar.DAY_OF_MONTH)
        val year = now.get(Calendar.YEAR)

        val phase = phaseOf(now)
        val week = if (phase == TermPhase.InTerm) termWeekOf(now) else null

        return Snapshot(
            dayOfWeek = dow,
            currentDateLabel = "$month" + "月$day" + "日 · 今天",
            dateLabel = "${MONTH_ABBR[month - 1]} $day",
            weekdayLabel = WEEKDAY_EN[dow - 1],
            weekNumber = week,
            weekLabel = week?.let { "Week $it" },
            phase = phase,
            termEndLabel = termEnd()?.let {
                "${it.get(Calendar.YEAR)}" + "年${it.get(Calendar.MONTH) + 1}" + "月${it.get(Calendar.DAY_OF_MONTH)}" + "日"
            },
            // 把"第几教学周"也告诉模型 —— 它要靠这个判断"本周"是第几周
            promptContext = "今天是 $year" + "年$month" + "月$day" + "日，" +
                    WEEKDAY_CN[dow - 1] + "（dayOfWeek=$dow）" +
                    (week?.let { "，现在是第 $it 教学周" } ?: ""),
            termKey = termStart()?.let { debugStamp(it) }.orEmpty(),
        )
    }

    // ---------------------------------------------------------------- LocalDate 支持
    //
    // 日期切换体系（左右滑 / 日历选日）以 LocalDate 为状态载体（脱糖后 minSdk 24 可用）。
    // 这里提供它与既有 Calendar 管线之间的转换 —— 周次 / 阶段判断全部复用老逻辑，
    // 不另起一套，避免两份实现漂移。

    /** [LocalDate] → 当天 0 点的 [Calendar]（本工程其余逻辑全部吃 Calendar）。 */
    fun toCalendar(date: LocalDate): Calendar = Calendar.getInstance().apply {
        clear()
        set(date.year, date.monthValue - 1, date.dayOfMonth)
    }

    /** 某一天的快照（星期几 / 教学周 / 学期阶段都按这一天算，不是按真实今天）。 */
    fun snapshot(date: LocalDate): Snapshot = snapshot(toCalendar(date))

    /**
     * 日期的后缀文案：「今天 / 昨天 / 明天 / 周X」。左右滑切日时，
     * Header 的日期要跟着选中日期走，不能永远挂着「今天」。
     */
    fun dateSuffix(date: LocalDate, today: LocalDate = LocalDate.now()): String =
        when (date) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            today.plusDays(1) -> "明天"
            // WEEKDAY_CN 本身就是「周一」这种完整写法，别再拼一个"周"字（测出来过双周）
            else -> WEEKDAY_CN[date.dayOfWeek.value - 1]
        }

    /** 手机端 Header 的日期文案："10月2日 · 周五"。 */
    fun currentDateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String =
        "${date.monthValue}月${date.dayOfMonth}日 · ${dateSuffix(date, today)}"

    /** 某个月的网格（周一开头，固定 6 行 42 格），日历面板直接渲。 */
    fun monthGrid(month: YearMonth): List<LocalDate> {
        val first = month.atDay(1)
        val start = first.minusDays((first.dayOfWeek.value - 1).toLong())   // 周一=1 → 回退到本周一
        return List(42) { i -> start.plusDays(i.toLong()) }
    }

    /** Calendar 的 `DAY_OF_WEEK` 是「周日=1」，这里统一成「周一=1 … 周日=7」。 */
    fun dayOfWeekOf(cal: Calendar): Int {
        val c = cal.get(Calendar.DAY_OF_WEEK)
        return if (c == Calendar.SUNDAY) 7 else c - 1
    }

    /**
     * 当前学期的第 1 教学周周一。**优先读 [TermInfoStore]**（用户导入课表时推断出来的真实值），
     * 读不到才回退到 [DEFAULT_TERM_START] 硬编码兜底。
     */
    fun termStart(): Calendar? {
        val stored = TermInfoStore.load()?.startDate?.let { parseIsoDate(it) }
        return stored ?: DEFAULT_TERM_START
    }

    /** 当前学期教学周总数。优先读 [TermInfoStore]，回退 [TERM_WEEKS]。 */
    fun termWeeks(): Int = TermInfoStore.load()?.weeks ?: TERM_WEEKS

    /** 学期最后一天（第 [termWeeks] 周的周日）。 */
    fun termEnd(): Calendar? {
        val start = termStart() ?: return null
        return (start.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, termWeeks() * 7 - 1)
        }
    }

    /**
     * 当前处在学期的哪个阶段。
     *
     * 学期起点未知（[termStart] 为 null）时一律当 [TermPhase.InTerm] ——
     * **宁可什么都不拦，也不要因为起点没配就让整个看板空掉。**
     */
    fun phaseOf(now: Calendar): TermPhase {
        val days = daysFromTermStart(now) ?: return TermPhase.InTerm
        if (days < 0) return TermPhase.BeforeTerm
        val week = (Math.floorDiv(days, 7L) + 1L).toInt()
        return if (week <= termWeeks()) TermPhase.InTerm else TermPhase.AfterTerm
    }

    /**
     * 学期第几周（第 1 周 = [termStart] 所在那周）。**只在教学周内返回非 null**。
     *
     * ⚠️ 这里有两个**踩过一次**的坑，别再改回去：
     *
     * 1. **必须先把时刻归零到当天 0 点**。[termStart] 是 0 点，但生产环境传进来的是
     *    `Calendar.getInstance()`（带时分秒）。9.13 晚上 23 点看课表时，天数差只有
     *    `-1 小时`，`/86_400_000` 之后变成 `0` → 又被算成第 1 周。
     * 2. **除法要用 `floorDiv`（向下取整），不能用 `/`**。Kotlin 的 `Int/Long` 除法是
     *    **向零截断**：`-1 / 7 == 0`，于是 9.13 得到 `0 + 1 = 第 1 周` —— 这正是
     *    「预备周的课混进第 1 周」的根因。`floorDiv(-1, 7) == -1` → 第 0 周 → 被
     *    `in 1..termWeeks()` 拦成 null，才是对的。
     */
    private fun termWeekOf(now: Calendar): Int? {
        val days = daysFromTermStart(now) ?: return null
        val week = (Math.floorDiv(days, 7L) + 1L).toInt()
        return if (week in 1..termWeeks()) week else null
    }

    /** 距 [termStart] 的**整天数**（负数 = 还没开学）。两边都归零到 0 点后再相减。 */
    private fun daysFromTermStart(now: Calendar): Long? {
        val start = termStart() ?: return null
        return Math.floorDiv(midnightOf(now) - start.timeInMillis, 86_400_000L)
    }

    /**
     * 把 "2026-09-14" 解析成当周 0 点的 Calendar。格式不对（不是 `YYYY-MM-DD`、
     * 月份/日期越界、非数字）返回 null，调用方回退兜底。
     */
    fun parseIsoDate(iso: String): Calendar? {
        val parts = iso.trim().split('-')
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (m !in 1..12 || d !in 1..31) return null
        val cal = Calendar.getInstance().apply {
            clear()
            // 设一个"宽松"的初始值再校验，避免 clear() 后 set 越界日期被静默进位
            set(y, m - 1, d)
        }
        // 校验回读是否一致（例如 "2026-02-31" 会被 Calendar 进位到 3 月，这里拦住）
        if (cal.get(Calendar.YEAR) != y || cal.get(Calendar.MONTH) != m - 1 || cal.get(Calendar.DAY_OF_MONTH) != d) {
            return null
        }
        return cal
    }

    /** 归零到当天 00:00，消掉"几点看的"对天数差的影响。 */
    private fun midnightOf(cal: Calendar): Long {
        val copy = cal.clone() as Calendar
        copy.set(Calendar.HOUR_OF_DAY, 0)
        copy.set(Calendar.MINUTE, 0)
        copy.set(Calendar.SECOND, 0)
        copy.set(Calendar.MILLISECOND, 0)
        return copy.timeInMillis
    }

    /** "08:00" → 480。解析不出来返回 [Int.MAX_VALUE]，排序时沉到最后。 */
    fun minutesOf(time: String): Int {
        val parts = time.split(':')
        if (parts.size != 2) return Int.MAX_VALUE
        val h = parts[0].trim().toIntOrNull() ?: return Int.MAX_VALUE
        val m = parts[1].trim().toIntOrNull() ?: return Int.MAX_VALUE
        return h * 60 + m
    }

    /** 当前时区的短格式，日志/缓存键用。 */
    fun debugStamp(now: Calendar = Calendar.getInstance()): String =
        String.format(Locale.US, "%04d-%02d-%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
}
