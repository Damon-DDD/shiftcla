//
//  ApiKeyStore.swift
//  Shiftcla
//
//  API Key 的本地存储。可观察，UI 直接读 isConfigured / current。
//  单独拆出来是因为 @Observable 宏依赖 SwiftUI/Observation 运行时，
//  与纯数据逻辑（Stores.swift）分开便于独立测试。
//

import Foundation
import Observation

@Observable
final class ApiKeyStore {
    static let shared = ApiKeyStore()

    private(set) var apiKey: String

    private init() {
        apiKey = ApiKeyStorage.current
    }

    /// 当前 Key；空串表示还没配置。
    var current: String { apiKey }

    /// 保存 Key。传空串等于清除。
    func save(_ key: String) {
        ApiKeyStorage.save(key)
        apiKey = ApiKeyStorage.current
    }

    /// 是否已经配置过。
    var isConfigured: Bool { ApiKeyStorage.isConfigured }
}


/// 接口地址与模型名（不绑定厂商，任何 OpenAI 兼容服务都能填）
enum ApiEndpointStorage {
    private static let endpointKey = "shiftcla.apiEndpoint"
    private static let modelKey = "shiftcla.apiModel"

    static var endpoint: String {
        get { UserDefaults.standard.string(forKey: endpointKey) ?? "https://api.deepseek.com/chat/completions" }
        set { UserDefaults.standard.set(newValue, forKey: endpointKey) }
    }

    static var model: String {
        get { UserDefaults.standard.string(forKey: modelKey) ?? "deepseek-chat" }
        set { UserDefaults.standard.set(newValue, forKey: modelKey) }
    }

    static func save(endpoint: String, model: String) {
        Self.endpoint = endpoint.trimmingCharacters(in: .whitespacesAndNewlines)
        Self.model = model.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}


/// AI 对话历史持久化（跨次打开保留）。只保留最近 20 轮，避免 token 无限增长。
enum ChatHistoryStore {
    private static let key = "shiftcla.chatHistory"
    private static let maxTurns = 20

    static func load() -> [ChatTurn] {
        guard let data = UserDefaults.standard.data(forKey: key),
              let turns = try? JSONDecoder().decode([ChatTurn].self, from: data) else { return [] }
        return turns
    }

    static func save(_ turns: [ChatTurn]) {
        let trimmed = Array(turns.suffix(maxTurns))
        if let data = try? JSONEncoder().encode(trimmed) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }

    static func clear() { UserDefaults.standard.removeObject(forKey: key) }
}
