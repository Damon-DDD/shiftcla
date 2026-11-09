//
//  CourseCard.swift
//  Shiftcla
//
//  课程卡片视图。对应 2026-10-23 新 Figma 稿：
//  时间 + 右上角状态（进行中 / tag）→ 课程名 → 📍地点，淡紫底圆角卡。
//

import SwiftUI

/// 单个课程卡片。
struct CourseCard: View {
    let course: Course
    /// 正在上课（当前时间落在课程时段内）→ 右上角显示「进行中」
    var inProgress: Bool = false
    var onTap: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // 顶部：时间 + 状态
            HStack(alignment: .top) {
                Text(course.timeRange)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(ShiftclaColor.textTertiary)
                Spacer()
                if inProgress {
                    Text(L.t("进行中", "Now"))
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(ShiftclaColor.primary)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 2)
                        .background(Color.white.opacity(0.85))
                        .clipShape(Capsule())
                } else if let tag = course.tag {
                    Text(tag)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(course.tagColor.fg)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 2)
                        .background(course.tagColor.bg)
                        .clipShape(Capsule())
                }
            }

            Spacer(minLength: 10)

            // 课程名
            Text(course.courseName)
                .font(.system(size: 19, weight: .bold))
                .foregroundColor(ShiftclaColor.textPrimary)
                .lineLimit(2)
                .minimumScaleFactor(0.8)

            Spacer(minLength: 10)

            // 地点
            if !course.location.isEmpty {
                HStack(spacing: 4) {
                    Image(systemName: "mappin")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(ShiftclaColor.textTertiary)
                    Text(course.location)
                        .font(.system(size: 12))
                        .foregroundColor(ShiftclaColor.textSecondary)
                        .lineLimit(1)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        // 新稿（2026-10 Figma）：卡片统一淡紫底，不再三色交替
        .background(ShiftclaColor.cardLavender)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .onTapGesture(perform: onTap)
    }
}
