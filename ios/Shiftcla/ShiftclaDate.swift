//
//  ShiftclaDate.swift
//  Shiftcla
//
//  日期/时间同步与学期周次逻辑。从 Android 版 `ShiftclaDate.kt` 忠实移植。
//
//  差异：java.util.Calendar → Foundation Calendar/DateComponents；
//        java.time.LocalDate → 自定义的轻量 LocalDate（避免 iOS 17 无 java.time）。
//

import Foundation

/// 相对当前学期的三个阶段。
enum TermPhase {
    case beforeTerm   // 学期还没开始（含预备周）
    case inTerm       // 处在 1..termWeeks 教学周之内
    case afterTerm    // 学期已经结束
}

/// 日期的轻量表示（年/月/日），替代 java.time.LocalDate。
struct LocalDate: Equatable, Hashable, Comparable {
    var year: Int
    var month: Int   // 1...12
    var day: Int     // 1...31

    static func < (lhs: LocalDate, rhs: LocalDate) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    /// 星期几：1=周一 … 7=周日。
    var dayOfWeek: Int {
        var comps = DateComponents()
        comps.year = year; comps.month = month; comps.day = day
        let cal = Calendar(identifier: .gregorian)
        let date = cal.date(from: comps)!
        let wd = cal.component(.weekday, from: date) // 1=周日...7=周六
        return wd == 1 ? 7 : wd - 1
    }

    func plusDays(_ n: Int) -> LocalDate {
        var comps = DateComponents()
        comps.year = year; comps.month = month; comps.day = day
        let cal = Calendar(identifier: .gregorian)
        let d = cal.date(byAdding: .day, value: n, to: cal.date(from: comps)!)!
        return LocalDate(date: d, cal: cal)
    }

    func minusDays(_ n: Int) -> LocalDate { plusDays(-n) }

    /// 距 self 的整天数（date - self）。
    func daysUntil(_ date: LocalDate) -> Int {
        var c1 = DateComponents(); c1.year = year; c1.month = month; c1.day = day
        var c2 = DateComponents(); c2.year = date.year; c2.month = date.month; c2.day = date.day
        let cal = Calendar(identifier: .gregorian)
        let d1 = cal.startOfDay(for: cal.date(from: c1)!)
        let d2 = cal.startOfDay(for: cal.date(from: c2)!)
        return Int(d2.timeIntervalSince(d1) / 86400)
    }

    init(year: Int, month: Int, day: Int) {
        self.year = year; self.month = month; self.day = day
    }

    init(date: Date, cal: Calendar = Calendar(identifier: .gregorian)) {
        year = cal.component(.year, from: date)
        month = cal.component(.month, from: date)
        day = cal.component(.day, from: date)
    }
}

/// 日期/时间同步与学期逻辑。
enum ShiftclaDate {

    // MARK: - 兜底默认值

    /// 第 1 教学周的周一。只是「用户还没导入过课表」时的兜底，真实值读 TermInfoStore。
    private static let defaultTermStart = LocalDate(year: 2026, month: 9, day: 14)

    /// 本学期教学周数。17 是从课表数据量出来的。
    static let termWeeksDefault = 17

    private static let monthAbbr = ["Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
    private static let weekdayCN = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]
    private static let weekdayEN = ["Monday", "Tuesday", "Wednesday", "Thursday",
                                    "Friday", "Saturday", "Sunday"]
    private static let weekdayENShort = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

    // MARK: - 快照

    /// 一次算好的日期快照（对应 Kotlin 的 Snapshot data class）。
    struct Snapshot {
        var dayOfWeek: Int                    // 1=周一 … 7=周日
        var currentDateLabel: String          // "10月1日 · 今天"
        var dateLabel: String                 // "Oct 1"
        var weekdayLabel: String              // "Thursday"
        var weekNumber: Int?                  // 当前教学周序号（1 起），非学期中为 nil
        var weekLabel: String?                // "Week 4"
        var phase: TermPhase
        var termEndLabel: String?             // "2027年1月10日"
        var promptContext: String             // 给提示词用的一句话
        var termKey: String                   // 季节性文案缓存键
    }

    static func snapshot(_ date: LocalDate, locale: String = AppLanguage.code) -> Snapshot {
        let dow = date.dayOfWeek
        let month = date.month
        let day = date.day
        let year = date.year

        let phase = phaseOf(date)
        let week = phase == .inTerm ? termWeekOf(date) : nil

        let isZh = locale == "zh"
        return Snapshot(
            dayOfWeek: dow,
            currentDateLabel: monthDayLabel(month, day, isZh) + " · " + (isZh ? "今天" : "Today"),
            dateLabel: "\(monthAbbr[month - 1]) \(day)",
            weekdayLabel: weekdayEN[dow - 1],
            weekNumber: week,
            weekLabel: week.map { "Week \($0)" },
            phase: phase,
            termEndLabel: termEnd().map { fullDateLabel($0.year, $0.month, $0.day, isZh) },
            promptContext: "今天是 \(year)年\(month)月\(day)日，" + weekdayCN[dow - 1] +
                "（dayOfWeek=\(dow)）" + (week.map { "，现在是第 \($0) 教学周" } ?? ""),
            termKey: termStart().map { debugStamp($0) } ?? ""
        )
    }

    // MARK: - 日期文案

    /// "10月24日" / "Oct 24"
    private static func monthDayLabel(_ month: Int, _ day: Int, _ isZh: Bool) -> String {
        isZh ? "\(month)月\(day)日" : "\(monthAbbr[month - 1]) \(day)"
    }

    /// "2027年1月10日" / "Jan 10, 2027"
    private static func fullDateLabel(_ year: Int, _ month: Int, _ day: Int, _ isZh: Bool) -> String {
        isZh ? "\(year)年\(month)月\(day)日" : "\(monthAbbr[month - 1]) \(day), \(year)"
    }

    /// 日期后缀：「今天 / 昨天 / 明天 / 周X」（英文 Today / Yesterday / Tomorrow / Mon）。
    static func dateSuffix(_ date: LocalDate, today: LocalDate? = nil, locale: String = AppLanguage.code) -> String {
        let t = today ?? LocalDate(date: Date())
        let isZh = locale == "zh"
        if date == t { return isZh ? "今天" : "Today" }
        if date == t.minusDays(1) { return isZh ? "昨天" : "Yesterday" }
        if date == t.plusDays(1) { return isZh ? "明天" : "Tomorrow" }
        let dow = date.dayOfWeek
        return isZh ? weekdayCN[dow - 1] : weekdayENShort[dow - 1]
    }

    /// 手机端 Header 日期："10月2日 · 周五" / "Oct 2 · Fri"。
    static func currentDateLabel(_ date: LocalDate, today: LocalDate? = nil, locale: String = AppLanguage.code) -> String {
        let isZh = locale == "zh"
        return monthDayLabel(date.month, date.day, isZh) + " · " + dateSuffix(date, today: today, locale: locale)
    }

    /// 平板端日期胶囊左半："Oct 13"（英文，品牌风格）
    static func enDateLabel(_ date: LocalDate) -> String {
        "\(monthAbbr[date.month - 1]) \(date.day)"
    }

    /// 平板端日期胶囊右半："Tuesday"
    static func enWeekdayLabel(_ date: LocalDate) -> String {
        weekdayEN[date.dayOfWeek - 1]
    }

    // MARK: - 学期边界

    /// 当前学期的第 1 教学周周一。优先读 TermInfoStore，回退硬编码。
    static func termStart() -> LocalDate? {
        if let stored = TermInfoStore.load(), let d = parseIsoDate(stored.startDate) {
            return d
        }
        return defaultTermStart
    }

    /// 教学周总数。优先读 TermInfoStore，回退 17。
    static func termWeeks() -> Int {
        TermInfoStore.load()?.weeks ?? termWeeksDefault
    }

    /// 学期最后一天（第 termWeeks 周的周日）。
    static func termEnd() -> LocalDate? {
        guard let start = termStart() else { return nil }
        return start.plusDays(termWeeks() * 7 - 1)
    }

    // MARK: - 阶段与周次

    /// 当前处在学期哪个阶段。
    static func phaseOf(_ date: LocalDate) -> TermPhase {
        guard let days = daysFromTermStart(date) else { return .inTerm }
        if days < 0 { return .beforeTerm }
        let week = (days / 7) + 1   // days 非负时用整除
        return week <= termWeeks() ? .inTerm : .afterTerm
    }

    /// 学期第几周（第 1 周 = termStart 所在那周）。只在教学周内返回非 nil。
    private static func termWeekOf(_ date: LocalDate) -> Int? {
        guard let days = daysFromTermStart(date) else { return nil }
        // 向下取整（处理负数）
        let week = Int(floor(Double(days) / 7.0)) + 1
        return (1...termWeeks()).contains(week) ? week : nil
    }

    /// 距 termStart 的整天数（负数 = 还没开学）。
    private static func daysFromTermStart(_ date: LocalDate) -> Int? {
        guard let start = termStart() else { return nil }
        return start.daysUntil(date)
    }

    // MARK: - ISO 日期解析

    /// 把 "2026-09-14" 解析成 LocalDate。格式不对 / 日期越界返回 nil。
    static func parseIsoDate(_ iso: String) -> LocalDate? {
        let parts = iso.trimmingCharacters(in: .whitespaces).split(separator: "-")
        guard parts.count == 3,
              let y = Int(parts[0]), let m = Int(parts[1]), let d = Int(parts[2]),
              (1...12).contains(m), (1...31).contains(d) else { return nil }

        // 用 DateComponents + Calendar 校验日期真实存在（拦 "2026-02-31" 这类越界日期）。
        // 对应 Android 版 Calendar「宽松模式回读校验」。
        var comps = DateComponents()
        comps.year = y; comps.month = m; comps.day = d
        let cal = Calendar(identifier: .gregorian)
        guard let date = cal.date(from: comps) else { return nil }
        // 回读：若输入是 2月31，Calendar 会进位成 3月2/3日，回读不一致 → 拒绝
        guard cal.component(.year, from: date) == y,
              cal.component(.month, from: date) == m,
              cal.component(.day, from: date) == d else { return nil }
        return LocalDate(year: y, month: m, day: d)
    }

    /// 时区无关的日期戳，缓存键用。
    static func debugStamp(_ date: LocalDate) -> String {
        String(format: "%04d-%02d-%02d", date.year, date.month, date.day)
    }

    /// 某个月份的网格（周一开头，固定 42 格）。
    static func monthGrid(month: Int, year: Int) -> [LocalDate] {
        let first = LocalDate(year: year, month: month, day: 1)
        let start = first.minusDays(first.dayOfWeek - 1)  // 回退到周一
        return (0..<42).map { start.plusDays($0) }
    }
}
