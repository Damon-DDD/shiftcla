//
//  DeepSeekEngine.swift
//  Shiftcla
//
//  把教务原始文本丢给 DeepSeek，拿回结构化课程。从 Android 版 `DeepSeekEngine.kt` 移植。
//
//  差异：Ktor → URLSession；kotlinx.serialization → Codable。
//  提示词、容错解析、周次推断逻辑**原样保留**。
//

import Foundation

enum DeepSeekEngine {

    // MARK: - 常量

    /// 接口地址与模型名均由用户配置（支持任何 OpenAI 兼容服务）
    static var endpoint: String { ApiEndpointStorage.endpoint }
    static var model: String { ApiEndpointStorage.model }

    /// 系统提示词（与 Android 版逐字一致）。
    static let systemPrompt =
        "你是一个无情的教务数据解析引擎。仅返回一个合法的 JSON 对象，绝不要包含 Markdown 标记（如 ```json）或任何其他文字。\n" +
        "【顶层结构】{ globalSummary: string, termStartDate: string|null, courses: Course[] }。\n" +
        "【termStartDate】学期第 1 教学周**周一**的日期，格式 YYYY-MM-DD（如 2026-09-14）。\n" +
        "    · 从课表文本 / 截图里的校历信息读（如「2026年秋季学期」「9月14日开学」「第1周 9.14-9.20」）；\n" +
        "    · **只认能明确对应到具体日期的**；读不到 / 课表里没写就填 null，**绝不要编造或猜一个日期**；\n" +
        "    · 注意「预备周 / 第0周」不算第 1 教学周，别把预备周周一的日期填进去。\n" +
        "【globalSummary】用极客或学长口吻，基于整体课表给出一句今日总评或建议，**限 20 字以内**；" +
        "课表很满/有考试要点出来，没什么特别的就轻松一句。\n" +
        "【courses】每个元素必须包含 11 个键：courseName, teacher, location, " +
        "dayOfWeek (1-7), startTime, endTime, startWeek, endWeek, weekParity, tag, aiNote。\n" +
        "【字段名·绝对禁止改写】上面这 11 个键名是唯一合法字段，**逐字符照抄，一个都不得改**。\n" +
        "    · 地点字段只能叫 location，禁止写成 classroom/room/place；\n" +
        "    · 时间字段只能叫 startTime/endTime（HH:mm），禁止写成 periodStart/periodEnd/classPeriods/节次数字；\n" +
        "    · 单双周字段只能叫 weekParity（值只能是 \"odd\"/\"even\"/null），禁止写成 weekType/单双周/parity；\n" +
        "    · 若课表只给了节次（如「第9-12节」），请用下面的【标准作息表】把节次换算成 startTime/endTime，\n" +
        "      **禁止自己猜时间**；课表直接给了具体时间就用课表的时间。\n" +
        "【标准作息表】第1节 08:10-08:55，第2节 09:05-09:50，第3节 10:10-10:55，第4节 11:05-11:50，" +
        "第5节 13:30-14:15，第6节 14:25-15:10，第7节 15:30-16:15，第8节 16:25-17:10，" +
        "第9节 18:20-19:05，第10节 19:10-19:55，第11节 20:00-20:45，第12节 20:50-21:35。\n" +
        "    · 例：第9-12节 → startTime=18:20, endTime=21:35；第1-2节 → startTime=08:10, endTime=09:50。\n" +
        "【硬性约定】\n" +
        "1. dayOfWeek 用 1-7 表示周一至周日，必须依据课表里出现的星期字样填写，不要一律写 1；\n" +
        "2. startTime / endTime 一律 24 小时制 HH:mm，补零（如 08:00、14:05），不要写「下午两点」；\n" +
        "3. **返回输入里能找到的全部课程**，不要只挑今天的 —— App 会自行按当天和当周筛选；\n" +
        "3.1 教务课表通常覆盖整个学期（如「1-16周」「单周」「双周」），**每门课都必须填周次：**\n" +
        "    · startWeek / endWeek 填教学周起止（含两端），例如「1-16周」→ startWeek=1, endWeek=16；\n" +
        "    · 单周填 weekParity=\"odd\"，双周填 \"even\"，每周都有填 null；\n" +
        "    · 输入没写周次时，startWeek=1、endWeek=20、weekParity=null（当作全学期）；\n" +
        "    · **同一门课跨多周只输出一条**，用 startWeek~endWeek 表示范围，" +
        "绝对不要每周输出一条，也不要把别的周的课复制到本周；\n" +
        "    · 不要自己按周次或星期筛选，全部输出，App 会筛。\n" +
        "4. tag 只在确有「测验/考试/线上/重点/上机」等标记时填，否则给 null；\n" +
        "5. aiNote 写一句对这门课有用的提醒（如「今天有随堂测验」），没有就给 null；\n" +
        "6. location / teacher 缺失时给空字符串，不要编造。\n" +
        "【空输入】用户没给有效课表时，courses 返回空数组，globalSummary 说明没看懂输入。"

    /// 多轮修改补充规则。
    static let adjustPrompt =
        "\n【多轮修改】这是一次连续对话。用户可能要求你**修改**已有课表（换教室、改时间、删掉某门课、" +
        "加一门课、调整顺序等）。此时你会收到一段「当前课表」上下文，请**在它的基础上做增量修改**，" +
        "并始终返回**修改后的完整课表**（不是只返回改动的那几条）。\n" +
        "用户只是提问（比如「周三有什么课」）时，不要改动课表，原样返回当前课表，" +
        "并把回答写进 globalSummary。"

    private static let currentSchedulePrefix = "当前课表（JSON）："

    /// 写「季节性文案」用的提示词（要人话，不是 JSON）。
    static let termNotePrompt =
        "你是课表 App「Shiftcla」里的学长，说话简短、口语、有温度，不说教也不客套。" +
        "只输出一句话：不要引号、不要 Markdown、不要换行、不要分段、不要解释你在做什么。"

    // MARK: - 提示词组装

    static func buildSystemPrompt(todayContext: String? = nil, locale: String = AppLanguage.code) -> String {
        var s = ""
        if let ctx = todayContext, !ctx.isEmpty { s += ctx + "\n" }
        s += systemPrompt
        s += oneOffCourseRule
        s += outputLanguageRule(locale)
        return s
    }

    /// 一次性课程规则。没有这条，模型会把「这个星期四加节课」理解成每周四。
    private static let oneOffCourseRule = """

【一次性课程｜必须遵守，优先于其他周次默认规则】
1. 当用户说「这周 / 本周 / 今天 / 明天 / 后天 / 这周四 / 下周三 / 某个具体日期」时，
   表示该课程**只在这一周发生一次**。
2. 新增或修改这类课程时，必须把 startWeek 和 endWeek **都设为当前教学周**
   （提示词开头已给出「现在是第 N 教学周」，直接用那个 N），
   **禁止留空、禁止写成 1..20**。
3. 在 aiNote 里写清「仅第 N 周，非常规课」，让用户能看出这是一次性的。
4. 只有在用户明确说「每周四 / 每星期四 / 固定 / 整个学期」时，才使用默认的整学期周次（1..20）。

例：用户说「这个星期四晚上加一节课」
→ dayOfWeek=4，startWeek=N，endWeek=N（N = 当前教学周），aiNote=「仅第 N 周，非常规课」
错：startWeek=1，endWeek=20 ← 这会让每个星期四都多出这节课
"""

    private static func outputLanguageRule(_ locale: String) -> String {
        if locale == "zh" { return "" }
        return "\n【输出语言】globalSummary 与每条 aiNote 必须用**英文**书写；" +
               "courseName / teacher / location 等字段保持原始数据里的语言，不要翻译。"
    }

    // MARK: - 网络请求体

    /// 一条消息。content 支持 String（纯文本）或 [ContentPart]（带图）。
    struct ChatMessage: Codable {
        let role: String
        let content: Content
    }

    /// content 的联合：纯文本 or 数组。
    enum Content: Codable {
        case text(String)
        case parts([Part])

        func encode(to encoder: Encoder) throws {
            var c = encoder.singleValueContainer()
            switch self {
            case .text(let s): try c.encode(s)
            case .parts(let p): try c.encode(p)
            }
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.singleValueContainer()
            if let s = try? c.decode(String.self) { self = .text(s); return }
            let p = try c.decode([Part].self)
            self = .parts(p)
        }
    }

    /// 图片 / 文本段（OpenAI Vision 兼容格式）。
    struct Part: Codable {
        let type: String
        let text: String?
        let image_url: ImageURL?
    }

    struct ImageURL: Codable {
        let url: String
    }

    private struct ChatRequest: Codable {
        let model: String
        let messages: [ChatMessage]
        let stream: Bool
    }
    private struct ChatResponse: Codable {
        let choices: [ChatChoice]
    }

    private struct ChatChoice: Codable {
        let message: ResponseMessage
        let finish_reason: String?
    }

    private struct ResponseMessage: Codable {
        let role: String
        let content: String
    }

    // MARK: - 核心调用

    /// 把教务原始文本解析成课程列表。失败返回 nil。
    static func parseSchedule(
        rawText: String,
        apiKey: String = "",
        imageBase64s: [String] = [],
        model: String = model
    ) async -> ScheduleResponse? {
        await chat(
            history: [ChatTurn(role: "user", content: rawText)],
            currentScheduleJson: nil,
            apiKey: apiKey,
            imageBase64s: imageBase64s,
            model: model
        )
    }

    /// 一轮对话：既是首次解析，也是后续「改课表」。
    static func chat(
        history: [ChatTurn],
        currentScheduleJson: String? = nil,
        apiKey: String = "",
        imageBase64s: [String] = [],
        model: String = model
    ) async -> ScheduleResponse? {
        let key = apiKey.isEmpty ? ApiKeyStorage.current : apiKey
        guard !key.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }

        let images = imageBase64s.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        guard !history.isEmpty, !(history.last!.content.isEmpty && images.isEmpty) else { return nil }

        do {
            let messages = buildMessages(history: history, currentScheduleJson: currentScheduleJson, images: images)
            let content = try await post(messages: messages, apiKey: key, model: model)
            return parseContent(content)
        } catch {
            return nil
        }
    }

    /// 多轮对话：把完整历史发给模型，返回纯文本回复。
    ///
    /// 上下文由 `messages` 数组承载，**与模型厂商无关** —— 任何 OpenAI 兼容的
    /// Chat Completions 服务（DeepSeek / OpenAI / OpenRouter / 硅基流动 / 智谱 …）
    /// 都支持。差异只在上下文窗口大小和是否支持图片。
    static func chatReply(
        history: [ChatTurn],
        apiKey: String = "",
        model: String = model
    ) async -> String? {
        let key = apiKey.isEmpty ? ApiKeyStorage.current : apiKey
        guard !key.trimmingCharacters(in: .whitespaces).isEmpty, !history.isEmpty else { return nil }

        var msgs: [ChatMessage] = [
            ChatMessage(role: "system", content: .text(buildSystemPrompt()))
        ]
        msgs += history.map { ChatMessage(role: $0.role, content: .text($0.content)) }

        do {
            let content = try await post(messages: msgs, apiKey: key, model: model)
            let text = content.trimmingCharacters(in: .whitespacesAndNewlines)
            return text.isEmpty ? nil : text
        } catch {
            return nil
        }
    }

    /// 让 AI 写一句纯文本（不解析 JSON）。
    static func writeTermNote(userPrompt: String, apiKey: String = "", model: String = model) async -> String? {
        let key = apiKey.isEmpty ? ApiKeyStorage.current : apiKey
        guard !key.trimmingCharacters(in: .whitespaces).isEmpty,
              !userPrompt.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }

        do {
            let messages = [
                ChatMessage(role: "system", content: .text(termNotePrompt)),
                ChatMessage(role: "user", content: .text(userPrompt)),
            ]
            let content = try await post(messages: messages, apiKey: key, model: model)
            let cleaned = sanitizeNote(content)
            return cleaned.isEmpty ? nil : cleaned
        } catch {
            return nil
        }
    }

    // MARK: - HTTP

    private static func post(messages: [ChatMessage], apiKey: String, model: String) async throws -> String {
        var req = URLRequest(url: URL(string: endpoint)!)
        req.httpMethod = "POST"
        req.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.timeoutInterval = 60

        let body = ChatRequest(model: model, messages: messages, stream: false)
        req.httpBody = try JSONEncoder().encode(body)

        let (data, resp) = try await URLSession.shared.data(for: req)
        guard let http = resp as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            return ""
        }
        let decoded = try JSONDecoder().decode(ChatResponse.self, from: data)
        return decoded.choices.first?.message.content ?? ""
    }

    // MARK: - messages 组装

    static func buildMessages(history: [ChatTurn], currentScheduleJson: String? = nil, images: [String] = []) -> [ChatMessage] {
        var msgs: [ChatMessage] = []

        let base = buildSystemPrompt(todayContext: ShiftclaDate.snapshot(LocalDate(date: Date())).promptContext)
        let isFollowUp = !(currentScheduleJson ?? "").isEmpty
        msgs.append(ChatMessage(role: "system", content: .text(isFollowUp ? base + adjustPrompt : base)))

        if isFollowUp {
            msgs.append(ChatMessage(role: "system", content: .text(currentSchedulePrefix + currentScheduleJson!)))
        }

        for (index, turn) in history.enumerated() {
            let attachImages = index == history.count - 1 && turn.role == "user"
            if attachImages {
                msgs.append(ChatMessage(role: turn.role, content: buildUserContent(turn.content, images)))
            } else {
                msgs.append(ChatMessage(role: turn.role, content: .text(turn.content)))
            }
        }
        return msgs
    }

    // MARK: - user content 组装

    static func buildUserContent(_ rawText: String, _ imageBase64s: [String]) -> Content {
        let images = imageBase64s.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        guard !images.isEmpty else { return .text(rawText) }

        var parts: [Part] = [Part(type: "text", text: rawText, image_url: nil)]
        for b64 in images {
            parts.append(Part(type: "image_url", text: nil,
                              image_url: ImageURL(url: "data:image/jpeg;base64,\(b64)")))
        }
        return .parts(parts)
    }

    // MARK: - JSON 序列化

    private static let encoder: JSONEncoder = {
        let e = JSONEncoder()
        return e
    }()

    private static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        return d
    }()

    static func encodeSchedule(_ schedule: ScheduleResponse) -> String {
        guard let data = try? encoder.encode(schedule) else { return "{}" }
        return String(data: data, encoding: .utf8) ?? "{}"
    }

    static func decodeSchedule(_ jsonText: String) throws -> ScheduleResponse {
        let trimmed = jsonText.trimmingCharacters(in: .whitespacesAndNewlines)
        let data = Data(trimmed.utf8)

        // 两种输入都接：包裹对象，或裸数组
        let parsed: ScheduleResponse
        if trimmed.hasPrefix("[") {
            let courses = try decoder.decode([Course].self, from: data)
            parsed = ScheduleResponse(courses: courses)
        } else {
            parsed = try decoder.decode(ScheduleResponse.self, from: data)
        }

        // 补全派生字段：id、block、清洗 termStartDate / globalSummary
        var result = parsed
        result.globalSummary = parsed.globalSummary?.trimmingCharacters(in: .whitespacesAndNewlines)
        result.globalSummary = (result.globalSummary?.isEmpty ?? true) ? nil : result.globalSummary
        result.termStartDate = parsed.termStartDate?.trimmingCharacters(in: .whitespacesAndNewlines)
        result.termStartDate = (result.termStartDate?.isEmpty ?? true) ? nil : result.termStartDate

        result.courses = parsed.courses.enumerated().map { idx, course in
            var c = course
            if c.id.isEmpty { c.id = "ds-\(idx)" }
            if c.block == .morning && inferBlock(c.startTime) == .afternoon {
                c.block = .afternoon
            }
            return c
        }
        return result
    }

    /// 按开始时间判断上午/下午：12:00 之前算上午。
    static func inferBlock(_ startTime: String) -> CourseBlock {
        let hourStr = startTime.split(separator: ":").first.map(String.init) ?? ""
        guard let hour = Int(hourStr.trimmingCharacters(in: .whitespaces)) else { return .morning }
        return hour >= 12 ? .afternoon : .morning
    }

    // MARK: - 容错解析

    static func parseContent(_ content: String) -> ScheduleResponse? {
        do {
            return try decodeSchedule(extractJsonPayload(content))
        } catch {
            return nil
        }
    }

    /// 剥掉模型可能带上的"壳"，只留下 JSON 本体。
    static func extractJsonPayload(_ raw: String) -> String {
        var text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.hasPrefix("\u{FEFF}") { text.removeFirst() }

        // 去掉 Markdown 代码围栏：```json / ```JSON / ``` 都吃掉
        text = text.replacingOccurrences(of: "```[a-zA-Z]*[ \\t]*\\r?\\n?", with: "", options: .regularExpression)
        text = text.replacingOccurrences(of: "```", with: "")
        text = text.trimmingCharacters(in: .whitespacesAndNewlines)

        // 两种合法开头：`{...}` 或 `[...]`，判据是"谁先出现"
        let arrStart = text.firstIndex(of: "[")
        let objStart = text.firstIndex(of: "{")

        let start: String.Index?
        if let a = arrStart, let o = objStart {
            start = min(a, o)
        } else {
            start = arrStart ?? objStart
        }

        guard let s = start else { return text }

        let lastBracket = text.lastIndex(of: "]")
        let lastBrace = text.lastIndex(of: "}")
        let end: String.Index?
        if let k = lastBracket, let b = lastBrace {
            end = max(k, b)
        } else {
            end = lastBracket ?? lastBrace
        }

        guard let e = end, e > s else { return text }
        return String(text[s...e])
    }

    /// 把模型写的这句话收拾干净。
    static func sanitizeNote(_ raw: String) -> String {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        s = s.trimmingCharacters(in: CharacterSet(charactersIn: "\"'“”「」"))
        s = s.replacingOccurrences(of: "**", with: "")
        s = s.replacingOccurrences(of: "*", with: "")
        let lines = s.split(separator: "\n").map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
        return lines.joined(separator: " ").trimmingCharacters(in: .whitespaces)
    }
}
