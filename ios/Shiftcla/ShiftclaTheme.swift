//
//  ShiftclaTheme.swift
//  Shiftcla
//
//  设计令牌（色值 + 尺寸）。从 Android 版 `ShiftclaTokens` 忠实移植，
//  全部为截图实测值。
//

import SwiftUI

// MARK: - 颜色

enum ShiftclaColor {
    // 全局背景
    static let surfaceCompact = Color(red: 1.0, green: 0.984, blue: 0.996)       // #FFFBFE
    static let surfaceExpanded = Color(red: 0.980, green: 0.980, blue: 0.992)    // #FAFAFD

    // 卡片
    static let cardLavender = Color(red: 0.953, green: 0.937, blue: 1.0)         // #F3EFFF
    static let cardNeutral = Color(red: 0.957, green: 0.953, blue: 0.969)        // #F4F3F7
    static let cardWhite = Color.white                                             // #FFFFFF
    static let cardOutline = Color(red: 0.902, green: 0.878, blue: 0.914)        // #E6E0E9
    static let cardWide = Color(red: 0.969, green: 0.949, blue: 0.980)           // #F7F2FA

    // 主色
    static let primary = Color(red: 0.404, green: 0.314, blue: 0.643)            // #6750A4
    static let primaryContainer = Color(red: 0.918, green: 0.867, blue: 1.0)     // #EADDFF
    static let onPrimaryContainer = Color(red: 0.129, green: 0.0, blue: 0.365)   // #21005D
    static let brandPurple = Color(red: 0.365, green: 0.271, blue: 0.596)        // #5D4598

    // AI
    static let aiPromptText = Color(red: 0.404, green: 0.314, blue: 0.643)       // #6750A4
    static let aiInsightSurface = Color(red: 0.816, green: 0.737, blue: 1.0)     // #D0BCFF
    static let aiInsightOnSurface = Color(red: 0.133, green: 0.0, blue: 0.365)   // #22005D
    static let aiInsightBadge = Color(red: 0.965, green: 0.941, blue: 1.0)       // #F6F0FF

    // Tag
    static let tagAlertBg = Color(red: 1.0, green: 0.886, blue: 0.886)           // #FFE2E2
    static let tagAlertFg = Color(red: 0.624, green: 0.149, blue: 0.153)         // #9F2627
    static let tagAccentBg = Color(red: 0.918, green: 0.867, blue: 1.0)          // #EADDFF
    static let tagAccentFg = Color(red: 0.231, green: 0.106, blue: 0.443)        // #3B1B71

    // 文字
    static let textPrimary = Color(red: 0.110, green: 0.110, blue: 0.125)        // #1C1C20
    static let textSecondary = Color(red: 0.286, green: 0.271, blue: 0.310)      // #49454F
    static let textTertiary = Color(red: 0.420, green: 0.404, blue: 0.447)       // #6B6772

    // 分区标记
    static let sectionMorning = Color(red: 0.184, green: 0.490, blue: 0.192)     // #2F7D31
    static let sectionAfternoon = Color(red: 0.902, green: 0.322, blue: 0.0)     // #E65200
    static let sectionRule = Color(red: 0.965, green: 0.310, blue: 0.745)        // #F64FBE

    // 右侧工具栏（2026-10-23 新稿）：选中项蓝底蓝字
    static let railSelectedFg = Color(red: 0.184, green: 0.435, blue: 0.816)     // #2F6FD0
    static let railSelectedBg = Color(red: 0.863, green: 0.914, blue: 1.0)       // #DCE9FF
}

// MARK: - 尺寸

enum ShiftclaDim {
    static let cardRadius: CGFloat = 24
    static let tagRadius: CGFloat = 5
    static let fabSize: CGFloat = 56
    static let fabRadius: CGFloat = 20
    static let compactFabTrailing: CGFloat = 24
    static let compactFabBottom: CGFloat = 32
    static let expandedFabTrailing: CGFloat = 48
    static let expandedFabBottom: CGFloat = 48
    static let gridBottomPadding: CGFloat = 112
    static let compactGridPadding: CGFloat = 16
    static let compactGridSpacing: CGFloat = 12
    static let expandedGridPadding: CGFloat = 48
    static let expandedGridSpacing: CGFloat = 28

    /// 宽度断点：< 600dp 走 Compact（"今日课程"大标题那套），≥ 600dp 走 Expanded。
    static let compactMaxWidth: CGFloat = 600

    /// 最小卡宽：列数反推的依据。地点文字（"第一教学楼 A-301"）在更窄的卡里会被压成省略号。
    static let minCardWidth: CGFloat = 176
}

// MARK: - 卡片底色

extension CardTint {
    var surfaceColor: Color {
        switch self {
        case .lavender: return ShiftclaColor.cardLavender
        case .neutral: return ShiftclaColor.cardNeutral
        case .outlined: return ShiftclaColor.cardWhite
        case .wide: return ShiftclaColor.cardWide
        }
    }
}

// MARK: - Tag 配色

extension TagTone {
    var bg: Color {
        switch self {
        case .alert: return ShiftclaColor.tagAlertBg
        case .accent: return ShiftclaColor.tagAccentBg
        }
    }
    var fg: Color {
        switch self {
        case .alert: return ShiftclaColor.tagAlertFg
        case .accent: return ShiftclaColor.tagAccentFg
        }
    }
}
