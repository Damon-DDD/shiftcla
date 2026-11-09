//
//  Models.swift
//  Shiftcla
//
//  数据模型。从 Android 版 `ShiftclaData.kt` / `ShiftclaDashboard.kt` 忠实移植。
//  全部 Codable，直接对应 DeepSeek 返回的 JSON 结构。
//

import Foundation

// MARK: - 枚举

/// 课程所属时段。Expanded 布局据此把课程分到左右两个半区。
enum CourseBlock: String, Codable {
    case morning = "Morning"
    case afternoon = "Afternoon"
}

/// 卡片底色。手机端三种交替，平板端统一 Wide。
enum CardTint: String, Codable {
    case lavender = "Lavender"
    case neutral = "Neutral"
    case outlined = "Outlined"
    case wide = "Wide"
}

/// Tag 配色档位。tag 为 nil 时不显示 Tag。
enum TagTone: String, Codable {
    case alert = "Alert"
    case accent = "Accent"
}

// MARK: - 课程

/// 单个课程。
///
/// 除 AI 一定会给的字段外，其余全部有默认值，所以 AI 少给字段、或多给字段
/// （配合 JSONDecoder 忽略未知键）都不会抛异常。
/// `id` 也有默认值 —— AI 不负责编 id，由引擎在解析后补上。
struct Course: Codable, Equatable, Identifiable {
    var id: String = ""
    var startTime: String = ""
    var endTime: String = ""
    var courseName: String = ""
    var location: String = ""
    var tag: String? = nil
    var tagColor: TagTone = .accent
    var cardTint: CardTint = .lavender
    var block: CourseBlock = .morning
    var teacher: String = ""
    var chapter: String = ""
    var weekSlot: String = ""
    var aiNote: String? = nil
    var dayOfWeek: Int = 1
    /// 教学周次。默认 1..20 且不分单双周 = 覆盖全学期，即「周次缺失时当它每周都上」。
    var startWeek: Int = 1
    var endWeek: Int = 20
    var weekParity: String? = nil

    /// 成员初始化器：代码里手工构造 Course 用（演示数据等）。
    init(id: String = "", startTime: String = "", endTime: String = "",
         courseName: String = "", location: String = "", tag: String? = nil,
         tagColor: TagTone = .accent, cardTint: CardTint = .lavender,
         block: CourseBlock = .morning, teacher: String = "", chapter: String = "",
         weekSlot: String = "", aiNote: String? = nil, dayOfWeek: Int = 1,
         startWeek: Int = 1, endWeek: Int = 20, weekParity: String? = nil) {
        self.id = id
        self.startTime = startTime
        self.endTime = endTime
        self.courseName = courseName
        self.location = location
        self.tag = tag
        self.tagColor = tagColor
        self.cardTint = cardTint
        self.block = block
        self.teacher = teacher
        self.chapter = chapter
        self.weekSlot = weekSlot
        self.aiNote = aiNote
        self.dayOfWeek = dayOfWeek
        self.startWeek = startWeek
        self.endWeek = endWeek
        self.weekParity = weekParity
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(startTime, forKey: .startTime)
        try c.encode(endTime, forKey: .endTime)
        try c.encode(courseName, forKey: .courseName)
        try c.encode(location, forKey: .location)
        try c.encodeIfPresent(tag, forKey: .tag)
        try c.encode(tagColor, forKey: .tagColor)
        try c.encode(cardTint, forKey: .cardTint)
        try c.encode(block, forKey: .block)
        try c.encode(teacher, forKey: .teacher)
        try c.encode(chapter, forKey: .chapter)
        try c.encode(weekSlot, forKey: .weekSlot)
        try c.encodeIfPresent(aiNote, forKey: .aiNote)
        try c.encode(dayOfWeek, forKey: .dayOfWeek)
        try c.encode(startWeek, forKey: .startWeek)
        try c.encode(endWeek, forKey: .endWeek)
        try c.encodeIfPresent(weekParity, forKey: .weekParity)
    }

    /// 自定义解码：接受 AI 的各种字段叫法。
    /// 模型可能输出 classroom / classPeriods / weekType 等非约定名，
    /// 这里统一映射到我们的字段，避免信息被 Codable 静默丢弃。
    private enum CodingKeys: String, CodingKey {
        case id, startTime, endTime, courseName, location, tag, tagColor, cardTint
        case block, teacher, chapter, weekSlot, aiNote, dayOfWeek
        case startWeek, endWeek, weekParity
        // 别名
        case classroom, classPeriods, weekType, periods, time
        case periodStart, periodEnd, startPeriod, endPeriod, serialNumber
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? ""
        courseName = try c.decodeIfPresent(String.self, forKey: .courseName) ?? ""
        teacher = try c.decodeIfPresent(String.self, forKey: .teacher) ?? ""
        chapter = try c.decodeIfPresent(String.self, forKey: .chapter) ?? ""
        aiNote = try c.decodeIfPresent(String.self, forKey: .aiNote)
        tag = try c.decodeIfPresent(String.self, forKey: .tag)
        weekSlot = try c.decodeIfPresent(String.self, forKey: .weekSlot) ?? ""

        // 地点：优先 location，其次 classroom
        location = try c.decodeIfPresent(String.self, forKey: .location)
            ?? c.decodeIfPresent(String.self, forKey: .classroom) ?? ""

        // 周次：缺省仍走"整学期"默认（1..20）
        startWeek = try c.decodeIfPresent(Int.self, forKey: .startWeek) ?? 1
        endWeek = try c.decodeIfPresent(Int.self, forKey: .endWeek) ?? 20
        dayOfWeek = try c.decodeIfPresent(Int.self, forKey: .dayOfWeek) ?? 1

        // 单双周：weekType("all"/"odd"/"even") 或 weekParity
        let parity = try c.decodeIfPresent(String.self, forKey: .weekParity)
            ?? c.decodeIfPresent(String.self, forKey: .weekType)
        weekParity = Self.normalizeParity(parity)

        // 颜色 / 分区：模型很少给，走默认
        tagColor = (try? c.decodeIfPresent(TagTone.self, forKey: .tagColor)) ?? .accent
        cardTint = (try? c.decodeIfPresent(CardTint.self, forKey: .cardTint)) ?? .lavender
        block = (try? c.decodeIfPresent(CourseBlock.self, forKey: .block)) ?? .morning

        // 时间：优先 startTime/endTime；否则用 classPeriods（"9-12" 或 "3-4"）换算
        if let st = try c.decodeIfPresent(String.self, forKey: .startTime),
           let et = try c.decodeIfPresent(String.self, forKey: .endTime),
           !st.isEmpty, !et.isEmpty {
            startTime = st; endTime = et
        } else if let periods = try c.decodeIfPresent(String.self, forKey: .classPeriods)
                    ?? c.decodeIfPresent(String.self, forKey: .periods),
                  let (st, et) = Self.time(fromPeriods: periods) {
            startTime = st; endTime = et
        } else if let t = try c.decodeIfPresent(String.self, forKey: .time) {
            // 形如 "18:20-19:05"
            let parts = t.components(separatedBy: "-")
            startTime = parts.first?.trimmingCharacters(in: .whitespaces) ?? ""
            endTime = parts.count > 1 ? parts[1].trimmingCharacters(in: .whitespaces) : ""
        } else if let ps = try c.decodeIfPresent(Int.self, forKey: .periodStart)
                    ?? c.decodeIfPresent(Int.self, forKey: .startPeriod),
                  let pe = try c.decodeIfPresent(Int.self, forKey: .periodEnd)
                    ?? c.decodeIfPresent(Int.self, forKey: .endPeriod),
                  let (st, et) = Self.time(fromPeriods: "\(ps)-\(pe)") {
            // 数字型节次：periodStart/periodEnd 或 startPeriod/endPeriod
            startTime = st; endTime = et
        } else {
            startTime = ""; endTime = ""
        }
    }

    /// "all"→nil，"odd"/"单"→odd，"even"/"双"→even
    private static func normalizeParity(_ raw: String?) -> String? {
        guard let s = raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
              !s.isEmpty else { return nil }
        if s == "all" || s == "全" || s == "每周" { return nil }
        if s.contains("odd") || s.contains("单") { return "odd" }
        if s.contains("even") || s.contains("双") { return "even" }
        return s
    }

    /// 节次 → 起止时间。按常见大学课表（含北校区 08:10 作息）。
    private static let periodStart: [Int: String] = [
        1: "08:10", 2: "09:05", 3: "10:10", 4: "11:05",
        5: "13:30", 6: "14:25", 7: "15:30", 8: "16:25",
        9: "18:20", 10: "19:10", 11: "20:00", 12: "20:50"
    ]
    private static let periodEnd: [Int: String] = [
        1: "08:55", 2: "09:50", 3: "10:55", 4: "11:50",
        5: "14:15", 6: "15:10", 7: "16:15", 8: "17:10",
        9: "19:05", 10: "19:55", 11: "20:45", 12: "21:35"
    ]

    /// "9-12" / "3-4" / "1" → (start, end)
    private static func time(fromPeriods raw: String) -> (String, String)? {
        let cleaned = raw.replacingOccurrences(of: "节", with: "")
                          .trimmingCharacters(in: .whitespaces)
        let nums = cleaned.split(whereSeparator: { !$0.isNumber }).compactMap { Int($0) }
        guard let first = nums.first, let s = periodStart[first] else { return nil }
        let last = nums.last ?? first
        let e = periodEnd[last] ?? s
        return (s, e)
    }

    /// 卡片左上角的 `"08:00 - 09:35"`
    var timeRange: String { "\(startTime) - \(endTime)" }

    /// 这门课在第 `week` 教学周上不上。单双周、周次区间都在这里判。
    func matchesWeek(_ week: Int) -> Bool {
        guard (startWeek...endWeek).contains(week) else { return false }
        switch weekParity?.trimmingCharacters(in: .whitespaces).lowercased() {
        case "odd", "单", "单周": return week % 2 == 1
        case "even", "双", "双周": return week % 2 == 0
        default: return true
        }
    }
}

// MARK: - 顶层响应

/// AI 返回的顶层包裹对象。
struct ScheduleResponse: Codable, Equatable {
    /// 今日总评（限 20 字），nil = AI 没给
    var globalSummary: String?
    /// 课程数组。AI 偶尔只回 summary 漏掉 courses，给默认空数组兜住。
    var courses: [Course]
    /// 学期第 1 教学周周一的日期，ISO "2026-09-14"。读不到为 nil。
    var termStartDate: String?

    init(globalSummary: String? = nil, courses: [Course] = [], termStartDate: String? = nil) {
        self.globalSummary = globalSummary
        self.courses = courses
        self.termStartDate = termStartDate
    }

    /// 自定义解码：courses 缺省时给空数组（对应 Kotlin 的 `= emptyList()` 默认值），
    /// 避免 AI 只回 `{"globalSummary": "..."}` 时解码抛 MissingField 异常。
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        globalSummary = try c.decodeIfPresent(String.self, forKey: .globalSummary)
        courses = try c.decodeIfPresent([Course].self, forKey: .courses) ?? []
        termStartDate = try c.decodeIfPresent(String.self, forKey: .termStartDate)
    }
}

// MARK: - 对话

/// 一轮对话。只用于拼请求上下文，UI 不直接渲染它。
struct ChatTurn: Codable {
    var role: String   // "user" / "assistant"
    var content: String
}

/// 展示给用户的气泡。
struct ChatBubble {
    var fromUser: Bool
    var text: String
}

// MARK: - 学期信息

/// 学期信息（第 1 教学周周一 + 教学周总数）。
struct TermInfo: Codable {
    var startDate: String
    var weeks: Int
}
