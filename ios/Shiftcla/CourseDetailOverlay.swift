//
//  CourseDetailOverlay.swift
//  Shiftcla
//
//  课程详情浮层。对应 Figma 稿：背景整屏模糊，居中白卡 ——
//  时间 + 测验 tag → 课程名大标题 → 📍地点 👤教师 📖章节 📅教学周 → AI 备注行。
//

import SwiftUI

struct CourseDetailOverlay: View {
    let course: Course
    let onClose: () -> Void

    var body: some View {
        ZStack {
            // 背景整屏模糊 + 点击关闭
            Color.clear
                .background(.ultraThinMaterial)
                .ignoresSafeArea()
                .onTapGesture(perform: onClose)

            // 居中白卡
            VStack(alignment: .leading, spacing: 14) {
                // 时间 + tag
                HStack(alignment: .top) {
                    Text(course.timeRange)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(ShiftclaColor.textTertiary)
                    Spacer()
                    if let tag = course.tag {
                        Text(tag)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(ShiftclaColor.tagAlertFg)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(ShiftclaColor.tagAlertBg)
                            .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                    }
                }

                // 课程名
                Text(course.courseName)
                    .font(.system(size: 26, weight: .bold))
                    .foregroundColor(ShiftclaColor.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)

                Divider()
                    .background(ShiftclaColor.cardOutline)

                // 信息行
                VStack(alignment: .leading, spacing: 10) {
                    if !course.location.isEmpty {
                        infoRow(icon: "mappin", text: course.location)
                    }
                    if !course.teacher.isEmpty {
                        infoRow(icon: "person", text: course.teacher)
                    }
                    if !course.chapter.isEmpty {
                        infoRow(icon: "book.closed", text: course.chapter)
                    }
                    if !course.weekSlot.isEmpty {
                        infoRow(icon: "calendar", text: course.weekSlot)
                    }
                }

                // AI 备注
                if let note = course.aiNote, !note.isEmpty {
                    HStack(alignment: .bottom, spacing: 10) {
                        Text(AppLanguage.isZh ? "AI 备注：\(note)" : "AI note: \(note)")
                            .font(.system(size: 12))
                            .foregroundColor(ShiftclaColor.aiPromptText)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 0)
                        Image(systemName: "sparkles")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 28, height: 28)
                            .background(ShiftclaColor.primary)
                            .clipShape(Circle())
                    }
                }
            }
            .padding(20)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(color: .black.opacity(0.10), radius: 24, y: 8)
            .padding(.horizontal, 32)
            .transition(.scale(scale: 0.92).combined(with: .opacity))
        }
    }

    private func infoRow(icon: String, text: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 13))
                .foregroundColor(ShiftclaColor.textSecondary)
                .frame(width: 18)
            Text(text)
                .font(.system(size: 13))
                .foregroundColor(ShiftclaColor.textSecondary)
        }
    }
}
