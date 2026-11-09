//
//  ShiftclaDataTests.swift
//  Shiftcla
//
//  数据层单元测试。验证 Swift 移植与 Android 版行为一致。
//  用 swiftc 直接编译运行（不依赖 Xcode 工程）。
//

import Foundation
import Testing

@testable import Shiftcla

@Suite struct CourseWeekTests {
    @Test func 单双周匹配() {
        // 单周课只在奇数周上
        let odd = Course(courseName: "单周课", dayOfWeek: 1, startWeek: 1, endWeek: 16, weekParity: "odd")
        #expect(odd.matchesWeek(1) == true)
        #expect(odd.matchesWeek(2) == false)
        #expect(odd.matchesWeek(15) == true)

        // 双周课只在偶数周上
        let even = Course(courseName: "双周课", dayOfWeek: 1, startWeek: 1, endWeek: 16, weekParity: "even")
        #expect(even.matchesWeek(2) == true)
        #expect(even.matchesWeek(1) == false)

        // 无单双周 = 每周都上
        let every = Course(courseName: "每周", dayOfWeek: 1, startWeek: 1, endWeek: 16)
        #expect(every.matchesWeek(3) == true)
        #expect(every.matchesWeek(8) == true)
    }

    @Test func 周次区间外不上() {
        let c = Course(courseName: "x", dayOfWeek: 1, startWeek: 2, endWeek: 9)
        #expect(c.matchesWeek(1) == false)
        #expect(c.matchesWeek(2) == true)
        #expect(c.matchesWeek(9) == true)
        #expect(c.matchesWeek(10) == false)
    }

    @Test func 中文单双周也能识别() {
        let odd = Course(courseName: "x", dayOfWeek: 1, weekParity: "单周")
        #expect(odd.matchesWeek(1) == true)
        #expect(odd.matchesWeek(2) == false)
    }

    @Test func 默认周次覆盖全学期() {
        // 默认 startWeek=1 endWeek=20，任何 1..20 周都上
        let c = Course(courseName: "x", dayOfWeek: 1)
        #expect(c.matchesWeek(1) == true)
        #expect(c.matchesWeek(20) == true)
    }
}

@Suite struct ISOParseTests {
    @Test func 合法日期解析() {
        #expect(ShiftclaDate.parseIsoDate("2026-09-14") != nil)
        #expect(ShiftclaDate.parseIsoDate("2026-09-14")?.year == 2026)
        #expect(ShiftclaDate.parseIsoDate("2026-09-14")?.month == 9)
        #expect(ShiftclaDate.parseIsoDate("2026-09-14")?.day == 14)
    }

    @Test func 非法日期拒绝() {
        #expect(ShiftclaDate.parseIsoDate("2026-02-31") == nil)  // 2 月没有 31 号
        #expect(ShiftclaDate.parseIsoDate("2026-13-01") == nil)  // 13 月
        #expect(ShiftclaDate.parseIsoDate("2026-00-10") == nil)  // 0 月
        #expect(ShiftclaDate.parseIsoDate("") == nil)
        #expect(ShiftclaDate.parseIsoDate("abc") == nil)
    }
}

@Suite struct WeekdayTests {
    @Test func 星期几编号() {
        // 2026-10-05 是周一
        #expect(LocalDate(year: 2026, month: 10, day: 5).dayOfWeek == 1)
        // 2026-10-06 周二
        #expect(LocalDate(year: 2026, month: 10, day: 6).dayOfWeek == 2)
        // 2026-10-04 周日 = 7
        #expect(LocalDate(year: 2026, month: 10, day: 4).dayOfWeek == 7)
    }
}

@Suite struct JsonPayloadTests {
    @Test func 剥掉markdown围栏() {
        let raw = "```json\n[{\"courseName\":\"x\"}]\n```"
        let cleaned = DeepSeekEngine.extractJsonPayload(raw)
        #expect(cleaned.hasPrefix("["))
        #expect(!cleaned.contains("```"))
    }

    @Test func 剥掉前导废话() {
        let raw = "好的，以下是解析结果：[{\"courseName\":\"x\"}]"
        let cleaned = DeepSeekEngine.extractJsonPayload(raw)
        #expect(cleaned.hasPrefix("["))
        #expect(cleaned.hasSuffix("]"))
    }

    @Test func 裸数组解析() throws {
        let json = "[{\"courseName\":\"高数\",\"dayOfWeek\":1,\"startTime\":\"08:00\"}]"
        let schedule = try DeepSeekEngine.decodeSchedule(json)
        #expect(schedule.courses.count == 1)
        #expect(schedule.courses[0].courseName == "高数")
        #expect(schedule.courses[0].id == "ds-0")  // 自动补 id
    }

    @Test func 缺courses字段不崩() throws {
        let json = "{\"globalSummary\":\"今天很轻松\"}"
        let schedule = try DeepSeekEngine.decodeSchedule(json)
        #expect(schedule.globalSummary == "今天很轻松")
        #expect(schedule.courses.isEmpty)
    }

    @Test func block按时间推断() throws {
        let json = "[{\"courseName\":\"早课\",\"startTime\":\"08:00\"},{\"courseName\":\"晚课\",\"startTime\":\"18:20\"}]"
        let schedule = try DeepSeekEngine.decodeSchedule(json)
        #expect(schedule.courses[0].block == .morning)
        #expect(schedule.courses[1].block == .afternoon)
    }
}

@Suite struct PromptTests {
    @Test func 系统提示词包含周次三件套() {
        let p = DeepSeekEngine.systemPrompt
        #expect(p.contains("startWeek"))
        #expect(p.contains("endWeek"))
        #expect(p.contains("weekParity"))
        #expect(p.contains("dayOfWeek"))
    }

    @Test func 英文界面加输出语言规则() {
        let en = DeepSeekEngine.buildSystemPrompt(todayContext: nil, locale: "en")
        #expect(en.contains("英文"))
        let zh = DeepSeekEngine.buildSystemPrompt(todayContext: nil, locale: "zh")
        #expect(!zh.contains("输出语言"))
    }

    @Test func sanitizeNote清理干净() {
        let dirty = "\"**今天有随堂测验**\n复习第六章\""
        let clean = DeepSeekEngine.sanitizeNote(dirty)
        #expect(clean == "今天有随堂测验 复习第六章")
    }
}
