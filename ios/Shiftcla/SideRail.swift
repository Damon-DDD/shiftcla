//
//  SideRail.swift
//  Shiftcla
//
//  右侧竖向工具栏。对应 2026-10-23 新 Figma 稿（Duo 内外屏共用）：
//
//  从上到下：☰ 菜单 / 📖 今日课程（选中蓝底）/ 📅 日历
//           → 弹性留白 → 「回到当天」竖排胶囊 → ✦ 星标 FAB（贴右下角）
//
//  用户明确要求：Duo 的星标 FAB 必须在 D 形屏的右下角 —— 由本栏底部锚定实现。
//

import SwiftUI

struct SideRail: View {
    /// 当前是否在「今日课程」视图（对应 📖 高亮）
    var todaySelected: Bool = true
    var onMenu: () -> Void
    var onToday: () -> Void
    var onCalendar: () -> Void
    var onImport: () -> Void
    var onAIChat: () -> Void
    var glassNS: Namespace.ID? = nil
    var isCalendarOpen: Bool = false
    var onTermEdit: () -> Void = {}

    /// 工具栏与内容之间的间距（内容右侧要留出这条宽）
    static let width: CGFloat = 52   // 窄栏：按钮紧贴屏幕右边缘（WiFi 图标正下方）

    var body: some View {
        HStack(spacing: 0) {
            // Figma 稿：导轨与内容之间有一条细竖线
            Rectangle()
                .fill(ShiftclaColor.cardOutline)
                .frame(width: 1)

            VStack(spacing: 10) {
                // 原 ☰ 的位置空出来，汉堡下移到「小书本」原来的位置
                Spacer().frame(height: 40)

                if #available(iOS 26.0, *) {
                    Menu {
                        Button { onAIChat() } label: { Label(L.t("导入课表", "Import"), systemImage: "square.and.arrow.down") }
                        Button { onTermEdit() } label: { Label(L.t("学期设置", "Semester"), systemImage: "calendar.badge.clock") }
                        Button { onMenu() } label: { Label(L.t("打开侧边栏", "Open sidebar"), systemImage: "sidebar.left") }
                    } label: { plainRailIcon("line.3.horizontal") }
                    .glassEffect(.regular, in: .circle)

                    // 与备忘录「…」一致：系统 Menu，菜单从按钮里长出来
                    Menu {
                        Button { onToday() } label: { Label(L.t("回到今天", "Today"), systemImage: "book") }
                        Button { onCalendar() } label: { Label(L.t("打开完整日历", "Open calendar"), systemImage: "calendar") }
                    } label: { plainRailIcon("calendar") }
                    .glassEffect(.regular, in: .circle)
                } else {
                    railButton(icon: "line.3.horizontal", selected: false, action: onMenu)
                    railButton(icon: "calendar", selected: false, action: onCalendar)
                }

                Spacer(minLength: 24)

                nowBackPill
                fab
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
        .frame(width: Self.width)
    }


    /// Menu 的标签：只有图标，外壳由 glassEffect 提供
    private func plainRailIcon(_ name: String) -> some View {
        Image(systemName: name)
            .font(.system(size: 15, weight: .medium))
            .foregroundColor(ShiftclaColor.textSecondary)
            .frame(width: 40, height: 40)
    }

    // MARK: - 圆形图标按钮

    private func railButton(icon: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(selected ? ShiftclaColor.railSelectedFg : ShiftclaColor.textSecondary)
                .frame(width: 40, height: 40)
                .background(selected ? ShiftclaColor.railSelectedBg : Color.white)
                .clipShape(Circle())
                .overlay(Circle().stroke(selected ? Color.clear : ShiftclaColor.cardOutline, lineWidth: 1))
        }
    }

    // MARK: - 竖排「回到当天」

    private var nowBackPill: some View {
        Button(action: onToday) {
            Text("回\n到\n当\n天")
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(ShiftclaColor.textSecondary)
                .multilineTextAlignment(.center)
                .lineSpacing(1)
                .frame(width: 30)
                .padding(.vertical, 10)
                .background(Color.white)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(ShiftclaColor.cardOutline, lineWidth: 1))
        }
    }

    // MARK: - 星标 FAB（贴右下角）

    /// 星标按钮：iOS 26+ 用系统 Menu + 液态玻璃，
    /// 菜单会「从按钮里长出来」（与备忘录右上角「…」一致，系统自带形变）。
    @ViewBuilder
    private var fab: some View {
        if #available(iOS 26.0, *) {
            Button(action: onAIChat) {
                Image(systemName: "sparkles")
                    .font(.system(size: 19, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
            }
            .glassEffect(.regular.tint(ShiftclaColor.brandPurple), in: .circle)
        } else {
            Button(action: onAIChat) {
                Image(systemName: "sparkles")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
                    .background(ShiftclaColor.brandPurple)
                    .clipShape(Circle())
                    .shadow(color: ShiftclaColor.brandPurple.opacity(0.3), radius: 8, y: 3)
        }
    }
    }
}
