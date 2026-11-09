//
//  ShiftclaViewModel.swift
//  Shiftcla
//
//  主 ViewModel。从 Android 版 `ShiftclaViewModel.kt` 移植。
//  StateFlow → @Observable；viewModelScope → Task。
//

import Foundation
import Observation

@Observable
final class ShiftclaViewModel {

    // MARK: - 状态

    /// 当前课表。
    var schedule: ScheduleResponse

    /// 选中的日期（冷启动 = 今天）。
    var selectedDate: LocalDate

    /// 对话气泡（导入面板展示）。
    var bubbles: [ChatBubble] = []

    /// 错误提示。
    var errorMessage: String?

    /// 是否正在解析。
    var isParsing = false

    /// 学期结束后那句 AI 文案。
    var termNote: String?

    /// 对话历史（拼请求上下文用，不直接渲染）。
    private var transcript: [ChatTurn] = []

    // MARK: - 初始化

    init() {
        // 冷启动恢复：从磁盘读回课表，读不到给空
        schedule = ScheduleStore.load() ?? ScheduleResponse()
        selectedDate = LocalDate(date: Date())
        // `-demo` 启动参数：塞一份演示课表，用于验证大屏/小屏布局（不影响正常使用）
        if ProcessInfo.processInfo.arguments.contains("-demo") {
            schedule = Self.demoSchedule()
        }
    }

    /// 演示课表（对应 Android 版 ShiftclaSampleData.phoneDashboard 的 8 门课）。
    static func demoSchedule() -> ScheduleResponse {
        func c(_ name: String, _ dow: Int, _ start: String, _ end: String,
               _ loc: String, _ tint: CardTint, _ block: CourseBlock,
               tag: String? = nil, note: String? = nil) -> Course {
            var course = Course(
                startTime: start, endTime: end, courseName: name,
                location: loc, tag: tag, tagColor: tag == nil ? .accent : .alert,
                cardTint: tint, block: block, aiNote: note
            )
            // 演示课固定挂在「今天」，保证任何一天启动都能看到卡片
            course.dayOfWeek = LocalDate(date: Date()).dayOfWeek
            // ForEach 要求 id 唯一：演示数据不走 decodeSchedule，得自己编
            course.id = "demo-\(name)"
            return course
        }
        return ScheduleResponse(
            globalSummary: L.t("早八连上四节，中午必须眯一会", "Four straight morning classes — nap at noon"),
            courses: [
                c(L.t("数据结构与算法B", "Data Structures & Algorithms"), 3, "08:10", "09:50", L.t("经世楼-519", "Jingshi-519"), .lavender, .morning,
                  tag: L.t("测验", "Quiz"), note: L.t("今天有随堂测验，复习第六章", "Quiz today — review chapter 6")),
                c(L.t("马克思主义基本原理", "Principles of Marxism"), 3, "10:10", "11:50", L.t("经世楼-325", "Jingshi-325"), .neutral, .morning),
                c(L.t("线性代数", "Linear Algebra"), 3, "08:00", "09:35", L.t("教学楼 A-408", "Bldg A-408"), .outlined, .morning),
                c(L.t("大学英语听说", "College English"), 3, "10:00", "11:35", L.t("文科楼 B-202", "Arts B-202"), .lavender, .morning),
                c(L.t("计算机网络", "Computer Networks"), 3, "14:00", "15:35", L.t("思源楼-B306", "Siyuan-B306"), .neutral, .afternoon),
                c(L.t("人工智能导论", "Intro to AI"), 3, "15:50", "17:25", L.t("思源楼-A101", "Siyuan-A101"), .outlined, .afternoon),
                c(L.t("大学物理(II)", "University Physics (II)"), 3, "18:30", "20:05", L.t("格物楼-301", "Gewu-301"), .lavender, .afternoon),
            ]
        )
    }

    // MARK: - 派生

    /// 是否有任何课程（全局，非当天）。
    var hasAnyCourses: Bool { !schedule.courses.isEmpty }

    /// 当前选中日期的快照。
    var selectedSnap: ShiftclaDate.Snapshot {
        ShiftclaDate.snapshot(selectedDate)
    }

    // MARK: - 日期切换

    func selectDate(_ date: LocalDate) { selectedDate = date }
    func backToToday() { selectedDate = LocalDate(date: Date()) }

    /// 当天要上的课（按 dayOfWeek + 教学周筛选）。
    func todayCourses() -> [Course] {
        let snap = selectedSnap
        guard snap.phase == .inTerm, let week = snap.weekNumber else { return [] }
        return schedule.courses.filter { course in
            course.dayOfWeek == snap.dayOfWeek && course.matchesWeek(week)
        }
    }

    // MARK: - 导入 / 修改

    /// 把一段教务原始文本（或图片）交给 AI 解析并灌进课表。
    func submit(rawText: String, imageBase64s: [String]) async {
        let trimmed = rawText.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty && imageBase64s.isEmpty {
            errorMessage = Localized.msgEmptyInput
            return
        }
        if !ApiKeyStorage.isConfigured {
            errorMessage = Localized.msgNeedApiKey
            return
        }
        if isParsing { return }

        isParsing = true
        errorMessage = nil

        let history = transcript + [ChatTurn(role: "user", content: trimmed)]
        bubbles.append(ChatBubble(fromUser: true, text: trimmed))

        let currentJson = schedule.courses.isEmpty ? nil : DeepSeekEngine.encodeSchedule(schedule)

        let parsed = await DeepSeekEngine.chat(
            history: history,
            currentScheduleJson: currentJson,
            imageBase64s: imageBase64s
        )

        if let parsed {
            if parsed.courses.isEmpty {
                errorMessage = Localized.msgNoCourses
                bubbles.append(ChatBubble(fromUser: false, text: Localized.msgNoCoursesReply))
            } else {
                schedule = parsed
                ScheduleStore.save(parsed)
                // 用 AI 给的学期起点 + endWeek 最大值推断真实学期
                TermInfoStore.save(
                    startDate: parsed.termStartDate,
                    weeks: parsed.courses.map { $0.endWeek }.max() ?? 0
                )
                transcript = history + [ChatTurn(role: "assistant", content: DeepSeekEngine.encodeSchedule(parsed))]
                bubbles.append(ChatBubble(fromUser: false, text: summarize(parsed)))
            }
        } else {
            errorMessage = Localized.msgParseFailed
            bubbles.append(ChatBubble(fromUser: false, text: Localized.msgParseFailedReply))
        }

        isParsing = false
    }

    /// 清空课表（本地识别「清除所有课程」这类指令）。
    func clearAll() {
        schedule = ScheduleResponse()
        ScheduleStore.save(schedule)
        TermInfoStore.clear()
        transcript = []
        bubbles = []
    }

    // MARK: - 学期结束文案

    /// 确保「学期已经结束」那句话拿得到（幂等）。
    func ensureTermNote() async {
        let snap = selectedSnap
        guard snap.phase == .afterTerm else { return }

        if let cached = TermNote.cached(termKey: snap.termKey) {
            termNote = cached
            return
        }
        if !ApiKeyStorage.isConfigured {
            termNote = nil  // UI 用当前语言兜底文案顶上
            return
        }
        let written = await DeepSeekEngine.writeTermNote(userPrompt: TermNote.prompt(snapshot: snap))
        if let written {
            TermNote.save(termKey: snap.termKey, note: written)
            termNote = written
        } else {
            termNote = nil
        }
    }

    // MARK: - 私有

    /// AI 气泡的人话文案。
    private func summarize(_ response: ScheduleResponse) -> String {
        let head = String(format: Localized.msgUpdatedCount, response.courses.count)
        if let summary = response.globalSummary, !summary.isEmpty {
            return "\(head) · \(summary)"
        }
        return head
    }
}

// MARK: - 语言

/// App 语言。用户决策：先做中文版，默认中文；后续 i18n 改这里（或按系统语言）。
enum AppLanguage {
    /// 跟随系统语言：系统是中文 → 中文界面；其他 → 英文界面。
    ///
    /// 单一 App 支持双语（App Store 标准做法）。
    /// 调试时可用启动参数强制：`-AppleLanguages "(zh-Hans)"` / `"(en)"`；
    /// 编译开关 `LANG_EN` 仅用于强制出英文包做验证。
    static var code: String {
        #if LANG_EN
        return "en"
        #else
        let preferred = Locale.preferredLanguages.first ?? "en"
        return preferred.hasPrefix("zh") ? "zh" : "en"
        #endif
    }

    static var isZh: Bool { code == "zh" }
}

// MARK: - 本地化（界面文案，随语言切换）

/// 界面文案。Android 版用 strings.xml，这里先用内联双语，后续可换 String Catalog。
enum Localized {
    static var msgEmptyInput: String {
        isZh ? "内容为空，先粘贴点东西或选张图吧" : "Nothing to parse — paste something or pick an image first"
    }
    static var msgNeedApiKey: String {
        isZh ? "请先配置 API Key" : "Please set your API Key first"
    }
    static var msgParseFailed: String {
        isZh ? "解析失败：网络异常或 AI 没返回合法 JSON" : "Parse failed: network problem, or the AI didn't return valid JSON"
    }
    static var msgParseFailedReply: String {
        isZh ? "没解析成功，换个说法再试试？" : "That didn't work — try phrasing it differently?"
    }
    static var msgNoCourses: String {
        isZh ? "AI 没从这段内容里找到任何课程" : "The AI couldn't find any courses in that content"
    }
    static var msgNoCoursesReply: String {
        isZh ? "这段内容里我没找到课程" : "I couldn't find any courses in that"
    }
    static var msgUnknownError: String {
        isZh ? "未知错误" : "Unknown error"
    }
    static var msgUpdatedCount: String {
        isZh ? "已更新 %d 门课" : "Updated %d courses"
    }
    static var todayCourses: String {
        isZh ? "今日课程" : "Today's Courses"
    }
    static var importSchedule: String {
        isZh ? "导入教务数据" : "Import schedule"
    }
    static var apiSettings: String {
        isZh ? "API 配置" : "API settings"
    }
    static var morning: String {
        isZh ? "上午课程" : "Morning"
    }
    static var morningSub: String {
        isZh ? "上午" : "AM"
    }
    static var afternoon: String {
        isZh ? "下午课程" : "Afternoon"
    }
    static var afternoonSub: String {
        isZh ? "下午" : "PM"
    }
    static var noClassInSection: String {
        isZh ? "这个时段没有课" : "No classes in this section"
    }

    /// 当前界面是否中文。
    ///
    /// 用户决策：先做中文版。默认中文（无论系统语言），英文留作后续 i18n 扩展。
    static var isZh: Bool { AppLanguage.isZh }
}


/// 界面文案双语选择：L.t("中文", "English")
enum L {
    static func t(_ zh: String, _ en: String) -> String {
        AppLanguage.isZh ? zh : en
    }
}
