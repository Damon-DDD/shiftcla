@file:Suppress("SpellCheckingInspection")

package com.shiftcla.app.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.shiftcla.app.R

/**
 * API Key 配置弹窗。
 *
 * 纯 UI，不碰存储 —— 保存动作通过 [onSave] 抛给调用方，
 * 这样它既能用在真实流程里，也能在预览 / 测试里独立渲染。
 *
 * @param initialKey 打开时的已有 Key（一般传 `ApiKeyStore.current()`）
 * @param onSave     点"保存"，参数是用户输入的原文（由调用方 trim + 落盘）
 * @param onClear    点"清除"，把本地 Key 抹掉
 */
@Composable
fun ApiKeyDialog(
    initialKey: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    onClear: () -> Unit = {},
) {
    var text by remember(initialKey) { mutableStateOf(initialKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = {
            Text(
                text = stringResource(R.string.api_key_title),
                style = MaterialTheme.typography.titleMedium,
                color = ShiftclaTokens.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.api_key_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = ShiftclaTokens.TextTertiary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.api_key_multimodal_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = ShiftclaTokens.Primary,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text("sk-...", color = ShiftclaTokens.TextTertiary)
                    },
                    // 用密码样式遮住，避免旁边有人时被看到
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ShiftclaTokens.Primary,
                        unfocusedBorderColor = ImportSheetTokens.InputBorder,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        cursorColor = ShiftclaTokens.Primary,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }) {
                Text(
                    stringResource(R.string.api_key_save),
                    color = ShiftclaTokens.Primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { text = ""; onClear() }) {
                Text(stringResource(R.string.api_key_clear), color = ShiftclaTokens.TextTertiary)
            }
        },
    )
}
