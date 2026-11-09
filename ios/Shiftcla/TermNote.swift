//
//  TermNote.swift
//  Shiftcla
//
//  学期结束后那句「季节性文案」。从 Android 版 `TermNote.kt` 移植。
//  兜底文案已搬进 Localizable 字符串（随语言切换）。
//

import Foundation

enum TermNote {
    private static let keyPrefix = "term_note_"

    /// 读缓存的文案；没有（或 termKey 为空）返回 nil。
    static func cached(termKey: String) -> String? {
        guard !termKey.isEmpty else { return nil }
        let v = UserDefaults.standard.string(forKey: keyPrefix + termKey)
        return v?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? v : nil
    }

    /// 写入缓存。termKey / note 为空时静默忽略。
    static func save(termKey: String, note: String) {
        guard !termKey.isEmpty, !note.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        UserDefaults.standard.set(note, forKey: keyPrefix + termKey)
    }

    /// 拼出给 AI 的指令。
    static func prompt(snapshot: ShiftclaDate.Snapshot, locale: String = AppLanguage.code) -> String {
        var s = ""
        s += "今天是 " + termKeyDateLabel(snapshot.promptContext) + "。"
        s += "我这个学期（第 1-\(ShiftclaDate.termWeeks()) 教学周）"
        if let end = snapshot.termEndLabel {
            s += "已经在 \(end) 结束"
        } else {
            s += L.t("已经结束", "Ended")
        }
        s += "了。"
        s += "请写一句话（40 字以内）给我："
        s += "先说一句让我放松下来的话，再顺带提醒我导入下个学期的课表。"
        if locale != "zh" {
            s += " 用英文写（Write it in English）。"
        }
        return s
    }

    /// "今天是 2026年10月1日，周四（dayOfWeek=4）…" → "2026年10月1日，周四"
    private static func termKeyDateLabel(_ promptContext: String) -> String {
        let prefix = "今天是 "
        guard promptContext.hasPrefix(prefix) else { return promptContext }
        let rest = String(promptContext.dropFirst(prefix.count))
        return rest.components(separatedBy: "（").first?.trimmingCharacters(in: .whitespaces) ?? rest
    }
}
