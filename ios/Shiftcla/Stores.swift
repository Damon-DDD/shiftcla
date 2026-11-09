//
//  Stores.swift
//  Shiftcla
//
//  本地存储。从 Android 版 `ShiftclaData.kt` 的 ShiftclaPrefs / ApiKeyStore /
//  TermInfoStore / ScheduleStore 移植。
//
//  差异：SharedPreferences → UserDefaults；StateFlow → @Observable。
//

import Foundation

/// 全局存储。统一用一个 UserDefaults suite，多个 Store 共享同一份。
enum ShiftclaPrefs {
    static let defaults = UserDefaults.standard
    private static let fileKey = "shiftcla_prefs"
}

// MARK: - API Key 纯存储

/// API Key 的底层读写（无 @Observable 依赖，可独立测试）。
/// [ApiKeyStore]（@Observable，供 UI 观察）内部复用它。
enum ApiKeyStorage {
    private static let keyAPI = "deepseek_api_key"

    /// 当前 Key；空串表示还没配置。
    static var current: String {
        UserDefaults.standard.string(forKey: keyAPI) ?? ""
    }

    /// 保存 Key。传空串等于清除。
    static func save(_ key: String) {
        UserDefaults.standard.set(key.trimmingCharacters(in: .whitespacesAndNewlines), forKey: keyAPI)
    }

    /// 是否已经配置过。
    static var isConfigured: Bool {
        !current.trimmingCharacters(in: .whitespaces).isEmpty
    }
}

// MARK: - 学期信息

/// 从课表数据推断出的学期信息的本地存储。
///
/// 用途同 Android：用户导入课表时从 AI 的 termStartDate + 课程 endWeek 最大值
/// 推断真实学期，存进 UserDefaults；ShiftclaDate 优先读这里，读不到回退硬编码。
enum TermInfoStore {
    private static let keyStart = "term_start"
    private static let keyWeeks = "term_weeks"

    /// 读当前学期信息。没存过 / 非法返回 nil，调用方回退硬编码兜底。
    static func load() -> TermInfo? {
        let d = UserDefaults.standard
        guard let start = d.string(forKey: keyStart) else { return nil }
        let weeks = d.integer(forKey: keyWeeks)
        guard weeks > 0 else { return nil }
        guard ShiftclaDate.parseIsoDate(start) != nil else { return nil }
        return TermInfo(startDate: start, weeks: weeks)
    }

    /// 保存推断出的学期信息。非法输入静默忽略。
    static func save(startDate: String?, weeks: Int) {
        guard let startDate, !startDate.trimmingCharacters(in: .whitespaces).isEmpty, weeks > 0 else { return }
        guard ShiftclaDate.parseIsoDate(startDate) != nil else { return }
        let d = UserDefaults.standard
        d.set(startDate, forKey: keyStart)
        d.set(weeks, forKey: keyWeeks)
    }

    /// 清空推断出的学期信息。
    static func clear() {
        let d = UserDefaults.standard
        d.removeObject(forKey: keyStart)
        d.removeObject(forKey: keyWeeks)
    }
}

// MARK: - 课表

/// 课表的本地持久化（冷启动恢复）。序列化复用 DeepSeekEngine 的 encode/decode。
enum ScheduleStore {
    private static let keySchedule = "schedule_json"

    /// 读回持久化课表。没存过 / JSON 损坏返回 nil。
    static func load() -> ScheduleResponse? {
        guard let json = UserDefaults.standard.string(forKey: keySchedule),
              !json.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        return try? DeepSeekEngine.decodeSchedule(json)
    }

    /// 保存课表。空课表也照存。
    static func save(_ schedule: ScheduleResponse) {
        UserDefaults.standard.set(DeepSeekEngine.encodeSchedule(schedule), forKey: keySchedule)
    }

    /// 清空。
    static func clear() {
        UserDefaults.standard.removeObject(forKey: keySchedule)
    }
}
