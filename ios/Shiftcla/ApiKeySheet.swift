//
//  ApiKeySheet.swift
//  Shiftcla
//
//  API Key 弹窗。与 Android 版 ApiKeyDialog 保持一致（极简）。
//

import SwiftUI

struct ApiKeySheet: View {
    /// 由外部浮层控制关闭
    var onClose: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss

    private func close() { if let onClose { onClose() } else { dismiss() } }
    @State private var text = ApiKeyStorage.current

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("API Key")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(ShiftclaColor.textPrimary)

            Text(Localized.isZh ? L.t("填自己的 Key，只存在本机，不会上传。留空即等于不配置。", "Your key stays on this device and is never uploaded. Leave it blank to skip.") : "Stored on this device only, never uploaded. Leave blank to skip.")
                .font(.system(size: 13))
                .foregroundColor(ShiftclaColor.textTertiary)

            Text(Localized.isZh ? L.t("需要支持多模态（图文识别）的大模型 Key，否则课表截图解析不了。", "Requires a multimodal (vision-capable) model key, otherwise timetable screenshots can't be parsed.") : "Needs a multimodal (vision-capable) model key, otherwise timetable screenshots can't be parsed.")
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(ShiftclaColor.primary)

            TextField("sk-...", text: $text)
                .font(.system(size: 14))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(12)
                .background(ShiftclaColor.cardNeutral)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

            HStack {
                Button(Localized.isZh ? L.t("清除", "Clear") : "Clear") {
                    text = ""
                    ApiKeyStorage.save("")
                    close()
                }
                .foregroundColor(ShiftclaColor.textTertiary)

                Spacer()

                Button(Localized.isZh ? L.t("保存", "Save") : "Save") {
                    ApiKeyStorage.save(text)
                    close()
                }
                .foregroundColor(ShiftclaColor.primary)
                .fontWeight(.medium)
            }
        }
        .padding(24)
    }
}
