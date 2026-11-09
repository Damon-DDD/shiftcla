//
//  LiquidGlass.swift
//  Shiftcla
//
//  液态玻璃接入层。用户决策：最低 iOS 17+，液态玻璃降级。
//
//  液态玻璃（glassEffect）是 iOS 26 专属；旧系统回退普通毛玻璃
//  （ultraThinMaterial）。这里统一成一个 .shiftclaGlass() 修饰器，
//  避免每处写 if #available 散落。
//

import SwiftUI

extension View {
    /// 给「导航/悬浮层」元素加液态玻璃：iOS 26+ 用 glassEffect，旧系统降级普通毛玻璃。
    ///
    /// ⚠️ 设计规范：玻璃只用于悬浮层（FAB / 抽屉 / 底部栏 / 浮层），
    /// 不要套在内容卡片（课程卡片）上。
    @ViewBuilder
    func shiftclaGlass(
        cornerRadius: CGFloat = 24,
        shape: ShiftclaGlassShape = .rounded
    ) -> some View {
        if #available(iOS 26.0, *) {
            switch shape {
            case .circle:
                self.glassEffect(.regular, in: .circle)
            case .capsule:
                self.glassEffect(.regular, in: .capsule)
            case .rounded:
                self.glassEffect(.regular, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            case .rect:
                self.glassEffect(.regular, in: .rect)
            }
        } else {
            // iOS 17–25：降级为普通毛玻璃（半透明材质）
            self.background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        }
    }
}

/// 玻璃形状。
enum ShiftclaGlassShape {
    case circle
    case capsule
    case rounded
    case rect
}

#if DEBUG
// MARK: - 液态玻璃「弹开」效果 Demo
//
// 用启动参数 `-glassDemo` 进入，不影响正常界面。
// 效果：一个玻璃 FAB，点击后向上「弹」出三个玻璃按钮，
// 展开/收起过程中多个胶囊会先合并成一整块再分裂（liquid 感）。

@available(iOS 26.0, *)
struct GlassLiquidDemo: View {
    @Namespace private var ns
    @State private var open = ProcessInfo.processInfo.arguments.contains("-glassOpen")

    private struct DemoAction: Identifiable {
        let id: String
        let icon: String
        let tint: Color
    }

    private let actions: [DemoAction] = [
        .init(id: "import",   icon: "square.and.arrow.down", tint: .purple),
        .init(id: "calendar", icon: "calendar",              tint: .blue),
        .init(id: "today",    icon: "book",                  tint: .teal)
    ]

    var body: some View {
        ZStack {
            // 背景：给玻璃提供可折射的色彩
            LinearGradient(
                colors: [Color(red: 0.35, green: 0.30, blue: 0.85),
                         Color(red: 0.62, green: 0.36, blue: 0.85),
                         Color(red: 0.95, green: 0.55, blue: 0.70)],
                startPoint: .topLeading, endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 14) {
                ForEach(0..<3) { i in
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(.white.opacity(0.20))
                        .frame(height: 86)
                        .overlay(alignment: .leading) {
                            Text("课程卡片 \(i + 1)")
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundStyle(.white.opacity(0.95))
                                .padding(.leading, 20)
                        }
                }
                Spacer()
            }
            .padding(24)
            .allowsHitTesting(false)

            VStack {
                Spacer()
                HStack {
                    Spacer()
                    GlassEffectContainer(spacing: 16) {
                        VStack(spacing: 16) {
                            if open {
                                ForEach(actions) { a in
                                    Button { } label: {
                                        Image(systemName: a.icon)
                                            .font(.system(size: 19, weight: .semibold))
                                            .foregroundStyle(.white)
                                            .frame(width: 54, height: 54)
                                    }
                                    .glassEffect(.regular.tint(a.tint.opacity(0.55)), in: .circle)
                                    .glassEffectID(a.id, in: ns)
                                    .glassEffectTransition(.matchedGeometry)
                                }
                            }

                            Button {
                                withAnimation(.spring(response: 0.42, dampingFraction: 0.72)) {
                                    open.toggle()
                                }
                            } label: {
                                Image(systemName: "sparkles")
                                    .font(.system(size: 22, weight: .semibold))
                                    .foregroundStyle(.white)
                                    .frame(width: 60, height: 60)
                                    .rotationEffect(.degrees(open ? 90 : 0))
                            }
                            .glassEffect(.regular.tint(.purple), in: .circle)
                            .glassEffectID("fab", in: ns)
                        }
                    }
                    .padding(.trailing, 30)
                    .padding(.bottom, 46)
                }
            }

            VStack {
                Text(open ? "已展开 · 点星标收起" : "点右下角星标 · 液体弹开")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.white.opacity(0.85))
                    .padding(.top, 14)
                Spacer()
            }
            .allowsHitTesting(false)   // 提示层不能挡住下方按钮的点击
        }
        .task {
            // `-glassAuto`：自动循环展开/收起，便于录制动效
            guard ProcessInfo.processInfo.arguments.contains("-glassAuto") else { return }
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_200_000_000)
                withAnimation(.spring(response: 0.42, dampingFraction: 0.72)) { open = true }
                try? await Task.sleep(nanoseconds: 1_600_000_000)
                withAnimation(.spring(response: 0.42, dampingFraction: 0.72)) { open = false }
            }
        }
    }
}
#endif

// MARK: - AI 对话面板（星标按钮打开，从底部升起）

/// 与 AI 的对话窗口。用 `.sheet` 呈现，系统自带从底部升起的转场；
/// iOS 26 上用液态玻璃做底，旧系统用 ultraThinMaterial。
struct AIChatSheet: View {
    /// 由外部浮层控制关闭；为 nil 时用 sheet 的 dismiss
    var onClose: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss

    private func close() { if let onClose { onClose() } else { dismiss() } }

    @State private var input = ""
    @State private var sending = false
    @State private var history: [ChatTurn] = ChatHistoryStore.load()   // 跨次打开保留
    @State private var messages: [ChatBubble] = ChatHistoryStore.load().isEmpty
        ? [ChatBubble(fromUser: false, text: L.t("问我任何关于课表的事，比如「周三下午帮我空出来」。", "Ask me anything about your schedule — try clearing Wednesday afternoon."))]
        : ChatHistoryStore.load().map { ChatBubble(fromUser: $0.role == "user", text: $0.content) }

    var body: some View {
        VStack(spacing: 0) {
            header
            Divider().opacity(0.4)
            conversation
            inputBar
        }
        .glassCard(cornerRadius: 32)   // 与侧边栏/日历统一的液态玻璃
    }

    // MARK: 头部

    private var header: some View {
        HStack {
            Text(L.t("问 AI", "Ask AI"))
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(ShiftclaColor.textPrimary)
            Spacer()
            Button {
                history = []
                messages = [ChatBubble(fromUser: false, text: L.t("问我任何关于课表的事，比如「周三下午帮我空出来」。", "Ask me anything about your schedule — try clearing Wednesday afternoon."))]
                ChatHistoryStore.clear()
            } label: {
                Image(systemName: "trash")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(ShiftclaColor.textSecondary)
                    .frame(width: 32, height: 32)
                    .background(Circle().fill(.white.opacity(0.55)))
            }
            Button { close() } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(ShiftclaColor.textSecondary)
                    .frame(width: 32, height: 32)
                    .background(Circle().fill(.white.opacity(0.55)))
            }
        }
        .padding(.horizontal, 22)
        .padding(.top, 20)
        .padding(.bottom, 12)
    }

    // MARK: 消息列表

    private var conversation: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(Array(messages.enumerated()), id: \.offset) { idx, m in
                        bubble(m).id(idx)
                    }
                    if sending {
                        HStack(spacing: 6) {
                            ProgressView().scaleEffect(0.7)
                            Text(L.t("思考中…", "Thinking…"))
                                .font(.system(size: 13))
                                .foregroundColor(ShiftclaColor.textTertiary)
                        }
                        .padding(.horizontal, 4)
                    }
                }
                .padding(.horizontal, 22)
                .padding(.vertical, 18)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .onChange(of: messages.count) { _, newValue in
                withAnimation { proxy.scrollTo(newValue - 1, anchor: .bottom) }
            }
        }
    }

    private func bubble(_ m: ChatBubble) -> some View {
        HStack {
            if m.fromUser { Spacer(minLength: 40) }
            Text(m.text)
                .font(.system(size: 15))
                .foregroundColor(m.fromUser ? .white : ShiftclaColor.textPrimary)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background {
                    if m.fromUser {
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .fill(ShiftclaColor.brandPurple)
                    } else {
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .fill(.white.opacity(0.75))
                    }
                }
            if !m.fromUser { Spacer(minLength: 40) }
        }
    }

    // MARK: 输入栏

    private var inputBar: some View {
        HStack(spacing: 10) {
            TextField(L.t("说点什么…", "Type a message…"), text: $input, axis: .vertical)
                .font(.system(size: 15))
                .lineLimit(1...4)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(.white.opacity(0.8))
                )

            Button(action: send) {
                Image(systemName: "arrow.up")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(ShiftclaColor.brandPurple.opacity(sending ? 0.4 : 1)))
            }
            .disabled(sending)
        }
        .padding(.horizontal, 18)
        .padding(.top, 10)
        .padding(.bottom, 18)
    }

    private func send() {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !sending else { return }
        messages.append(ChatBubble(fromUser: true, text: text))
        history.append(ChatTurn(role: "user", content: text))
        input = ""
        sending = true
        Task {
            let reply = await DeepSeekEngine.chatReply(history: history)
            await MainActor.run {
                let answer = reply ?? L.t("没拿到回复，检查一下 API Key 设置。", "No response — check your API Key settings.")
                messages.append(ChatBubble(fromUser: false, text: answer))
                history.append(ChatTurn(role: "assistant", content: answer))
                ChatHistoryStore.save(history)
                sending = false
            }
        }
    }
}


// MARK: - 液态 morph 宿主

extension View {
    /// iOS 26+ 把子树放进同一个 GlassEffectContainer，使 glassEffectID 相同
    /// 的元素之间能发生「液态形变」；旧系统原样返回。
    @ViewBuilder
    func glassMorphHost() -> some View {
        if #available(iOS 26.0, *) {
            GlassEffectContainer(spacing: 18) { self }
        } else {
            self
        }
    }
}


extension View {
    /// 日历面板的玻璃底：iOS 26+ 与导轨按钮共用 glassEffectID，形成液态形变
    @ViewBuilder
    func glassCalendarPanel(id: String, ns: Namespace.ID) -> some View {
        if #available(iOS 26.0, *) {
            self.glassEffect(.regular, in: .rect(cornerRadius: 28))
                .glassEffectID(id, in: ns)
        } else {
            self.background(
                .ultraThinMaterial,
                in: RoundedRectangle(cornerRadius: 28, style: .continuous)
            )
        }
    }
}


extension View {
    /// 浮层卡片玻璃底：iOS 26+ 用真·液态玻璃，旧系统回退材质
    @ViewBuilder
    func glassCard(cornerRadius: CGFloat = 32) -> some View {
        if #available(iOS 26.0, *) {
            self.glassEffect(.regular, in: .rect(cornerRadius: cornerRadius))
        } else {
            self.background(
                .ultraThinMaterial,
                in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            )
        }
    }
}


// MARK: - 学期设置（手动指定开学日期与周数）

struct TermEditPanel: View {
    var onClose: () -> Void

    @State private var startDate: Date
    @State private var weeks: Int

    private static let fmt: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }()

    init(onClose: @escaping () -> Void) {
        self.onClose = onClose
        let info = TermInfoStore.load()
        let iso = info?.startDate ?? "2026-09-14"
        _startDate = State(initialValue: TermEditPanel.fmt.date(from: iso) ?? Date())
        _weeks = State(initialValue: info?.weeks ?? 17)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(L.t("学期设置", "Semester"))
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(ShiftclaColor.textPrimary)

            Text(L.t("手动指定开学日期后，课表按此计算周次，不再依赖 AI 推断。", "Set the term start date manually; weeks are computed from it instead of AI inference."))
                .font(.system(size: 12))
                .foregroundColor(ShiftclaColor.textTertiary)

            HStack {
                Text(L.t("开学日期", "Term start")).font(.system(size: 14)).foregroundColor(ShiftclaColor.textSecondary)
                Spacer()
                DatePicker("", selection: $startDate, displayedComponents: .date)
                    .labelsHidden()
                    .datePickerStyle(.compact)
            }

            HStack {
                Text(L.t("学期周数", "Weeks")).font(.system(size: 14)).foregroundColor(ShiftclaColor.textSecondary)
                Spacer()
                Stepper("\(weeks) 周", value: $weeks, in: 1...30)
                    .labelsHidden()
                Text("\(weeks) 周").font(.system(size: 14, weight: .medium))
                    .foregroundColor(ShiftclaColor.textPrimary)
            }

            HStack {
                Button(L.t("取消", "Cancel")) { onClose() }
                    .foregroundColor(ShiftclaColor.textTertiary)
                Spacer()
                Button(L.t("保存", "Save")) {
                    TermInfoStore.save(startDate: TermEditPanel.fmt.string(from: startDate), weeks: weeks)
                    onClose()
                }
                .foregroundColor(ShiftclaColor.primary)
                .fontWeight(.medium)
            }
            .padding(.top, 4)
        }
        .padding(22)
    }
}
