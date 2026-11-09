//
//  ShiftclaCalendar.swift
//  Shiftcla
//
//  月历浮层。对应 Figma 稿「2026年10月」日历卡：
//  ‹ 年月 › 切月 + 「回到今天」胶囊 + 周一开头 42 格网格，
//  选中日 = 紫实心圆，今天 = 紫描边，非本月日期淡灰。
//

import SwiftUI

struct ShiftclaCalendarView: View {
    let selectedDate: LocalDate
    let onSelect: (LocalDate) -> Void
    let onClose: () -> Void

    @State private var displayedMonth: Int
    @State private var displayedYear: Int

    init(selectedDate: LocalDate, onSelect: @escaping (LocalDate) -> Void, onClose: @escaping () -> Void) {
        self.selectedDate = selectedDate
        self.onSelect = onSelect
        self.onClose = onClose
        _displayedMonth = State(initialValue: selectedDate.month)
        _displayedYear = State(initialValue: selectedDate.year)
    }

    /// 英文年月显示，如 "Oct 2026"
    private var monthYearLabel: String {
        let abbr = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
        let idx = max(0, min(11, displayedMonth - 1))
        return "\(abbr[idx]) \(String(displayedYear))"
    }

    private var weekdayHeader: [String] {
        AppLanguage.isZh
            ? ["一", "二", "三", "四", "五", "六", "日"]
            : ["S", "M", "T", "W", "T", "F", "S"]
    }

    var body: some View {
        VStack(spacing: 12) {
            // 顶部：‹ 年月 › + 回到今天
            HStack(spacing: 8) {
                Button { shiftMonth(-1) } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(ShiftclaColor.textSecondary)
                        .frame(width: 28, height: 28)
                }
                Text(AppLanguage.isZh ? "\(String(displayedYear))年\(displayedMonth)月" : monthYearLabel)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(ShiftclaColor.textPrimary)
                Button { shiftMonth(1) } label: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(ShiftclaColor.textSecondary)
                        .frame(width: 28, height: 28)
                }
                Spacer()
                // 「回到今天」紫描边胶囊
                Button { backToToday() } label: {
                    Text(L.t("回到今天", "Today"))
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(ShiftclaColor.primary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .overlay(
                            Capsule().stroke(ShiftclaColor.primary.opacity(0.5), lineWidth: 1)
                        )
                }
            }

            // 星期表头（周一开头）
            HStack(spacing: 0) {
                ForEach(weekdayHeader, id: \.self) { w in
                    Text(w)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(ShiftclaColor.textTertiary)
                        .frame(maxWidth: .infinity)
                }
            }

            // 日期网格（42 格，周一开头）
            let grid = ShiftclaDate.monthGrid(month: displayedMonth, year: displayedYear)
            let today = LocalDate(date: Date())
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: 7), spacing: 6) {
                ForEach(grid, id: \.self) { date in
                    let inMonth = date.month == displayedMonth
                    let isSelected = date == selectedDate
                    let isToday = date == today
                    Text("\(date.day)")
                        .font(.system(size: 13, weight: isSelected ? .semibold : .regular))
                        .foregroundColor(dayColor(inMonth: inMonth, isSelected: isSelected))
                        .frame(maxWidth: .infinity)
                        .frame(height: 34)
                        .background(dayBackground(isSelected: isSelected, isToday: isToday))
                        .clipShape(Circle())
                        .overlay {
                            // 今天：紫描边圆圈（选中态用实心，不叠描边）
                            if isToday && !isSelected {
                                Circle().stroke(ShiftclaColor.primary, lineWidth: 1.5)
                            }
                        }
                        .onTapGesture {
                            onSelect(date)
                            onClose()
                        }
                }
            }
        }
        .padding(16)
    }

    private func dayColor(inMonth: Bool, isSelected: Bool) -> Color {
        if isSelected { return .white }
        if !inMonth { return ShiftclaColor.textTertiary.opacity(0.35) }
        return ShiftclaColor.textPrimary
    }

    private func dayBackground(isSelected: Bool, isToday: Bool) -> Color {
        isSelected ? ShiftclaColor.primary : Color.clear
    }

    private func shiftMonth(_ delta: Int) {
        var m = displayedMonth + delta
        var y = displayedYear
        if m < 1 { m = 12; y -= 1 }
        if m > 12 { m = 1; y += 1 }
        displayedMonth = m
        displayedYear = y
    }

    private func backToToday() {
        let today = LocalDate(date: Date())
        displayedMonth = today.month
        displayedYear = today.year
        onSelect(today)
        onClose()
    }
}
