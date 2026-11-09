//
//  ImportSheet.swift
//  Shiftcla
//
//  导入面板。对应 Figma 稿：grip 横条 + 居中「SHIFTCLA」标题 + 大输入框
//  （placeholder：粘贴课表文本、作息时间、或者直接发大白话告诉 AI...）
//  + 「导入课表图片」描边胶囊 + 右下「交给 AI 解析」紫胶囊。
//
//  截图选图走 PhotosPicker → loadTransferable(Data.self) → base64。
//

import SwiftUI
import PhotosUI

struct ImportSheet: View {
    let viewModel: ShiftclaViewModel

    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var imageDataList: [Data] = []

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            // grip 横条
            HStack {
                Spacer()
                Capsule()
                    .fill(ShiftclaColor.cardOutline)
                    .frame(width: 36, height: 4)
                Spacer()
            }
            .padding(.top, 8)

            // 居中品牌标题
            Text("SHIFTCLA")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(ShiftclaColor.brandPurple)
                .kerning(2)
                .frame(maxWidth: .infinity)

            // 对话气泡（有历史时显示）
            if !viewModel.bubbles.isEmpty {
                ScrollView {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(viewModel.bubbles.indices, id: \.self) { i in
                            let b = viewModel.bubbles[i]
                            HStack {
                                if b.fromUser { Spacer() }
                                Text(b.text)
                                    .font(.system(size: 13))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(b.fromUser ? ShiftclaColor.primary : ShiftclaColor.textTertiary)
                                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                                if !b.fromUser { Spacer() }
                            }
                        }
                    }
                }
                .frame(maxHeight: 140)
            }

            // 大输入框（描边圆角）+ 两行占位文案
            ZStack(alignment: .topLeading) {
                if text.isEmpty {
                    Text("粘贴课表文本、作息时间、\n或者直接发大白话告诉 AI...")
                        .font(.system(size: 13))
                        .foregroundColor(ShiftclaColor.textTertiary)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 22)
                        .allowsHitTesting(false)
                }
                TextField("", text: $text, axis: .vertical)
                    .font(.system(size: 13))
                    .foregroundColor(ShiftclaColor.textPrimary)
                    .padding(14)
                    .frame(minHeight: 96, alignment: .topLeading)
            }
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(ShiftclaColor.cardOutline, lineWidth: 1))

            // 选中的图片缩略
            if !imageDataList.isEmpty {
                HStack(spacing: 8) {
                    ForEach(imageDataList.indices, id: \.self) { i in
                        if let ui = UIImage(data: imageDataList[i]) {
                            Image(uiImage: ui)
                                .resizable()
                                .scaledToFill()
                                .frame(width: 52, height: 52)
                                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        }
                    }
                    Spacer()
                }
            }

            // 底部：导入课表图片（左） + 交给 AI 解析（右）
            HStack {
                PhotosPicker(selection: $pickerItems, maxSelectionCount: 4, matching: .images) {
                    HStack(spacing: 6) {
                        Image(systemName: "photo.on.rectangle.angled")
                            .font(.system(size: 12, weight: .semibold))
                        Text("导入课表图片")
                            .font(.system(size: 12, weight: .medium))
                    }
                    .foregroundColor(ShiftclaColor.textPrimary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9)
                    .background(Color.white)
                    .clipShape(Capsule())
                    .overlay(Capsule().stroke(ShiftclaColor.cardOutline, lineWidth: 1))
                }
                .onChange(of: pickerItems) { _, items in
                    Task { await loadImages(items) }
                }

                Spacer()

                Button(action: submit) {
                    HStack(spacing: 4) {
                        Text(viewModel.bubbles.isEmpty ? L.t("交给 AI 解析", "Parse with AI") : L.t("交给 AI 修改", "Ask AI to edit"))
                            .font(.system(size: 13, weight: .semibold))
                        Image(systemName: "chevron.down")
                            .font(.system(size: 11, weight: .semibold))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 11)
                    .background(viewModel.isParsing ? ShiftclaColor.brandPurple.opacity(0.6) : ShiftclaColor.brandPurple)
                    .clipShape(Capsule())
                }
                .disabled(viewModel.isParsing || (text.trimmingCharacters(in: .whitespaces).isEmpty && imageDataList.isEmpty))
            }

            // 错误提示
            if let error = viewModel.errorMessage {
                Text(error)
                    .font(.system(size: 12))
                    .foregroundColor(ShiftclaColor.tagAlertFg)
            }
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 16)
        .background(ShiftclaColor.surfaceCompact)
        .presentationDetents([.height(420), .large])
        .presentationDragIndicator(.hidden)
    }

    private func loadImages(_ items: [PhotosPickerItem]) async {
        imageDataList = []
        for item in items {
            if let data = try? await item.loadTransferable(type: Data.self) {
                imageDataList.append(data)
            }
        }
    }

    private func submit() {
        let base64s = imageDataList.map { $0.base64EncodedString() }
        Task {
            await viewModel.submit(rawText: text, imageBase64s: base64s)
            text = ""
            imageDataList = []
            pickerItems = []
        }
    }
}
