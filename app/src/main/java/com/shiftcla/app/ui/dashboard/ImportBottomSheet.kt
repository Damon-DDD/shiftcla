@file:Suppress("SpellCheckingInspection")

package com.shiftcla.app.ui.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.widthIn
import com.shiftcla.app.data.ChatBubble

/* ============================================================================
 *  ImportBottomSheet · 把一段文本（可附一张图）交给 AI 解析
 *  ---------------------------------------------------------------------------
 *  浅色模式。布局自上而下：
 *    1. 居中深色标题 "SHIFTCLA"
 *    2. 选中图片时的缩略图（右上角 X 取消）
 *    3. 占满宽度的多行输入框，**左下角**一个相册按钮
 *    4. 右下角亮紫色实心按钮「交由 AI 解析 ✨」
 *
 *  拆成 [ImportBottomSheet] + [ImportSheetContent] 两层，是为了让内容层
 *  能脱离 ModalBottomSheet 单独离屏渲染做视觉校验（弹窗类在 layoutlib 里不好截）。
 * ========================================================================== */

/** 面板的尺寸与色值。 */
object ImportSheetTokens {
    val SheetColor = Color(0xFFFFFFFF)
    val TitleColor = Color(0xFF1C1C20)
    val TitleSize = 16.sp

    /** 亮紫色：比主题主色 #6750A4 更鲜艳，按钮要"跳"出来 */
    val AccentPurple = Color(0xFF7C4DFF)

    val InputMinHeight = 190.dp
    val InputShape = RoundedCornerShape(16.dp)
    val InputBorder = Color(0xFFE0DCEC)
    val InputBorderFocused = Color(0xFF7C4DFF)

    val ButtonShape = RoundedCornerShape(percent = 50)
    val ThumbSize = 64.dp
}

/** 输入框占位符。用户说的话就是提示词，所以写得很宽松。 */
private const val HINT = "粘贴教务文本、作息时间，或直接用大白话告诉 AI..."

/** 一次最多选几张图。系统选择器本身的上限是 MediaStore 给的值（通常 100），这里收得更紧。 */
private const val MAX_IMAGES = 9

/** 送进模型前先把图压到这个边长以内，避免 base64 过大被拒（也省 token）。 */
private const val IMAGE_MAX_DIM = 1280
private const val IMAGE_JPEG_QUALITY = 85

/**
 * 完整弹窗：包 [ImportSheetContent]。
 *
 * @param onSubmit 点「交由 AI 解析 ✨」时回调：`(文本, 图片 Base64 列表，可为空)`
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportBottomSheet(
    onDismiss: () -> Unit,
    onSubmit: (String, List<String>) -> Unit,
    modifier: Modifier = Modifier,
    bubbles: List<ChatBubble> = emptyList(),
) {
    // sheetState 在内部建：一旦写进对外签名，SheetState 这个实验性类型
    // 会顺着类型系统把调用方也拉进 @OptIn(ExperimentalMaterial3Api) 里。
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.imePadding(),
        sheetState = sheetState,
        containerColor = ImportSheetTokens.SheetColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,   // 设计里没有那根横杠
    ) {
        ImportSheetContent(onSubmit = onSubmit, bubbles = bubbles)
    }
}

/**
 * 面板内容（纯视觉 + 图片选择）。
 *
 * @param onSubmit         点按钮时回调，参数为当前文本与**所有**已选图片的 Base64 列表
 * @param initialText      预填文本（测试 / 预览用）
 * @param initialImageUris 预选图片（测试 / 预览用）
 */
@Composable
fun ImportSheetContent(
    onSubmit: (String, List<String>) -> Unit,
    modifier: Modifier = Modifier,
    initialText: String = "",
    initialImageUris: List<Uri> = emptyList(),
    /** 已有对话。非空时面板会变成"继续对话 / 改课表"的形态 */
    bubbles: List<ChatBubble> = emptyList(),
) {
    val t = ImportSheetTokens
    val context = LocalContext.current
    var text by remember { mutableStateOf(initialText) }
    // 用 List 而不是单个 Uri：相册能一次选多张，也支持分几次追加
    var pickedUris by remember { mutableStateOf(initialImageUris) }

    // 多选契约。注意必须用 PickMultipleVisualMedia —— 单选版 PickVisualMedia()
    // 一次只回一个 Uri，这也是之前"只能传一张图"的直接原因。
    // Android 13+ 走系统 Photo Picker，低版本自动退回 ACTION_OPEN_DOCUMENT + EXTRA_ALLOW_MULTIPLE。
    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES),
    ) { uris ->
        if (uris.isNotEmpty()) {
            // 追加去重 + 截断到上限（用户可能分多次选）
            pickedUris = (pickedUris + uris).distinct().take(MAX_IMAGES)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 28.dp, bottom = 20.dp)
            .navigationBarsPadding(),
    ) {
        // ---------- 标题：居中、深色 ----------
        Text(
            text = "SHIFTCLA",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = t.TitleSize),
            color = t.TitleColor,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))

        // ---------- 对话历史：有过交互才出现，首轮不占地方 ----------
        if (bubbles.isNotEmpty()) {
            ConversationList(bubbles)
            Spacer(Modifier.height(16.dp))
        }

        // ---------- 已选图片：横向一排缩略图，张数多也能滑 ----------
        if (pickedUris.isNotEmpty()) {
            PickedImageStrip(
                uris = pickedUris,
                onRemove = { uri -> pickedUris = pickedUris.filterNot { it == uri } },
            )
            Spacer(Modifier.height(12.dp))
        }

        // ---------- 大输入框 + 左下角相册按钮 ----------
        Box(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = t.InputMinHeight),
                placeholder = {
                    Text(
                        text = HINT,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ShiftclaTokens.TextTertiary,
                    )
                },
                shape = t.InputShape,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = t.InputBorderFocused,
                    unfocusedBorderColor = t.InputBorder,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    cursorColor = t.InputBorderFocused,
                ),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        pickImages.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    // 已经选满就置灰，避免用户白点一次
                    enabled = pickedUris.size < MAX_IMAGES,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddPhotoAlternate,
                        contentDescription = "从相册选择图片（可多选）",
                        tint = when {
                            pickedUris.isEmpty() -> ShiftclaTokens.TextTertiary
                            pickedUris.size >= MAX_IMAGES -> ShiftclaTokens.TextTertiary.copy(alpha = 0.4f)
                            else -> t.AccentPurple
                        },
                    )
                }
                // 只有选过图才显示计数，平时不占视觉
                if (pickedUris.isNotEmpty()) {
                    Text(
                        text = "${pickedUris.size}/$MAX_IMAGES",
                        style = MaterialTheme.typography.labelSmall,
                        color = ShiftclaTokens.TextTertiary,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---------- 右下角主按钮 ----------
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    // 转 Base64 放在点击这一刻做：只有真提交时才付这份代价。
                    // 逐张转，失败的（比如文件被删）直接跳过，不连累其余图片。
                    val base64s = pickedUris.mapNotNull { uriToBase64Jpeg(context, it) }
                    onSubmit(text, base64s)
                    // 提交后清空输入框和已选图：下一轮"继续改课表"该是干净的空输入，
                    // 而不是上一轮残留的文字（用户报的「对话后内容还在对话框里」）。
                    text = ""
                    pickedUris = emptyList()
                },
                enabled = text.isNotBlank() || pickedUris.isNotEmpty(),
                shape = t.ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = t.AccentPurple,
                    contentColor = Color.White,
                    disabledContainerColor = t.AccentPurple.copy(alpha = 0.35f),
                    disabledContentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text(
                    // 首轮是"解析"，之后就是"改" —— 文案跟着语义走
                    text = if (bubbles.isEmpty()) "交由 AI 解析 ✨" else "发送修改 ✨",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * 对话气泡列表。
 *
 * 限高 + 可滚动：不然聊几轮就把输入框挤出屏幕。
 * 用户靠右、AI 靠左，用底色区分而不是描边，视觉更安静。
 */
@Composable
private fun ConversationList(bubbles: List<ChatBubble>) {
    val t = ImportSheetTokens
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(bubbles) { bubble ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (bubble.fromUser) Arrangement.End else Arrangement.Start,
            ) {
                Text(
                    text = bubble.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bubble.fromUser) Color.White else ShiftclaTokens.TextPrimary,
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 14.dp,
                                topEnd = 14.dp,
                                bottomStart = if (bubble.fromUser) 14.dp else 4.dp,
                                bottomEnd = if (bubble.fromUser) 4.dp else 14.dp,
                            )
                        )
                        .background(if (bubble.fromUser) t.AccentPurple else ShiftclaTokens.CardNeutral)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** 一排缩略图，每张右上角带取消按钮；超过屏宽可横向滑。 */
@Composable
private fun PickedImageStrip(uris: List<Uri>, onRemove: (Uri) -> Unit) {
    val t = ImportSheetTokens
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(uris, key = { it.toString() }) { uri ->
            PickedImageThumb(uri = uri, onClear = { onRemove(uri) }, size = t.ThumbSize)
        }
    }
}

/** 缩略图 + 右上角取消按钮。 */
@Composable
private fun PickedImageThumb(uri: Uri, onClear: () -> Unit, size: Dp) {
    val t = ImportSheetTokens
    val context = LocalContext.current
    // 只在 uri 变化时解码一次，别每次重组都读盘
    val bitmap = remember(uri) { decodeScaledBitmap(context, uri) }

    Box(Modifier.size(size)) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(ShiftclaTokens.CardNeutral),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "已选图片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    tint = ShiftclaTokens.TextTertiary,
                )
            }
        }
        // 取消按钮：贴在右上角，像个徽标
        IconButton(
            onClick = onClear,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .background(Color(0xFF1C1C20), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "取消选择图片",
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/**
 * 把相册里的图读成一张**缩边后的 Bitmap**。
 *
 * 送模型前必须缩：手机原图动辄 4000px，直接 base64 会好几 MB，
 * 既慢又容易被服务端按体积拒掉。
 */
internal fun decodeScaledBitmap(context: Context, uri: Uri, maxDim: Int = IMAGE_MAX_DIM): Bitmap? =
    runCatching {
        val src = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        }
        when {
            src == null -> null
            maxOf(src.width, src.height) <= maxDim -> src
            else -> {
                val scale = maxDim.toFloat() / maxOf(src.width, src.height)
                val w = (src.width * scale).toInt().coerceAtLeast(1)
                val h = (src.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(src, w, h, true)
            }
        }
    }.getOrNull()

/**
 * 把相册里的图转成 JPEG 的 Base64（不含 `data:` 前缀）。
 *
 * 失败返回 null —— 上层按"没选图"处理，不至于因为一张图整条链路崩掉。
 */
internal fun uriToBase64Jpeg(
    context: Context,
    uri: Uri,
    maxDim: Int = IMAGE_MAX_DIM,
    quality: Int = IMAGE_JPEG_QUALITY,
): String? = runCatching {
    val bitmap = decodeScaledBitmap(context, uri, maxDim)
    if (bitmap == null) {
        null
    } else {
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}.getOrNull()
