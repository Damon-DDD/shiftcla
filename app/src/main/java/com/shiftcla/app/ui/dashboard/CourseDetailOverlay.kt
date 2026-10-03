@file:Suppress("SpellCheckingInspection")

package com.shiftcla.app.ui.dashboard

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.shiftcla.app.data.ApiKeyStore
import com.shiftcla.app.data.ShiftclaDate
import com.shiftcla.app.data.TermPhase
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/* ============================================================================
 *  CourseDetailOverlay · 课程详情（覆盖在主看板之上的全屏浮层）
 *  ---------------------------------------------------------------------------
 *  容器式转场（同 Android 12+ 桌面打开 App 的手感），全部由**一个**进度值 p 驱动：
 *
 *    p = 0   浮层里的卡片正好落在"被点中的那张卡"的矩形上，
 *            并且盖着一层**源卡底色的快照**（看起来就是原来那张卡）
 *    p → 1   卡片长到居中的详情卡，快照淡出、详情内容显现，
 *            底图同步变糊、遮罩淡入
 *
 *  另外两个一起动的东西（都在 [ShiftclaApp] 里）：
 *    · 被点中的那张卡随 (1 - p) 淡出 —— 否则卡片"飞走"后原位置还留着一个残影
 *    · 底图模糊半径 20dp × p
 *
 *  因为只有一个 p，动画天然可打断：p 还在往上走时点一下关闭，
 *  `animateTo(0f)` 直接从当前值反转，不会跳帧。
 *
 *  实测规格（手机稿 402×874 @81%、折叠屏稿 1280×800 @60.3%，ICC→sRGB 校正后取色）
 *    卡片      手机：左右各留 32dp（≡ 宽 338dp）；大屏：宽 420dp 封顶、水平垂直居中
 *              圆角 28dp、纯白 #FFFFFF、弥散投影
 *    内距      左右 24dp，上下 20dp
 *    时间      13sp  #6B6772（实测墨迹宽 73.6dp）
 *    Tag       30.6 × 15.9dp，圆角 5dp，#FFE2E2 / #9F2627
 *    课程名    28sp Bold #1C1C20，行高 34sp（实测墨迹 109.1 × 27.0dp）
 *    分隔线    1dp #F0EBF3，撑满内容宽（实测 292.9dp）
 *    信息行    4 行，行高 24dp + 间距 8dp（实测行距 31.8dp）；图标 16dp outline，
 *              文字 16sp #49454F（实测墨迹高 14.7dp）
 *    AI 区     左：浅紫 #F3EFFF 圆角 20dp 面板（实测 216.7 × 62.5dp），文字 13sp/18sp #6750A4
 *              右：62 × 62dp Squircle #EBE3FF + 22dp 星标 #6750A4（实测按钮 62.9 × 61.8dp）
 *    整卡高度  365dp（实测 364.5dp，在手机稿里正好垂直居中）
 * ========================================================================== */

/** 详情浮层的色值与尺寸，全部为截图实测值。 */
object ShiftclaDetailTokens {
    /** 遮罩：极浅半透明白，让模糊后的底图透出来 */
    val Scrim = Color.White.copy(alpha = 0.4f)

    /** 底图高斯模糊最大半径。Modifier.blur 只在 Android 12+ 真正生效，
     *  低版本自动退化为"不模糊"，由上面那层 Scrim 兜底。 */
    val BackdropBlur = 20.dp

    val CardSurface = Color(0xFFFFFFFF)
    val CardRadius = 28.dp
    val CardElevation = 24.dp
    val CardMaxWidth = 420.dp
    val CardMarginHorizontal = 32.dp
    val CardPaddingH = 24.dp
    val CardPaddingV = 20.dp

    val Divider = Color(0xFFF0EBF3)

    val TitleSize = 28.sp
    val TitleLineHeight = 34.sp
    val MetaSize = 13.sp
    val BodySize = 16.sp
    val InfoRowHeight = 24.dp
    val InfoRowGap = 8.dp
    val InfoIconSize = 16.dp
    val InfoIconGap = 8.dp

    val AiPanelColor = Color(0xFFF3EFFF)
    val AiPanelRadius = 20.dp
    val AiTextColor = Color(0xFF6750A4)
    val AiTextSize = 13.sp
    val AiTextLineHeight = 18.sp
    val AiButtonColor = Color(0xFFEBE3FF)
    val AiButtonSize = 62.dp
    val AiButtonRadius = 20.dp
    val AiButtonIconSize = 22.dp
    val AiGap = 12.dp

    /** 展开：稍慢、带减速尾巴，像桌面 App 的窗口展开 */
    const val OPEN_MILLIS = 340
    /** 收起：快一点，手感更利落 */
    const val CLOSE_MILLIS = 250
    /** p 小于这段时，卡片上盖的"源卡快照"逐渐显现（≡ 展开初期的反向交叉淡化） */
    const val SNAPSHOT_FADE = 0.28f
    /** p 小于这段时，整张卡片淡出，避免和底下的原卡片硬切 */
    const val CARD_TAIL_FADE = 0.16f
}

/** 被点中那张卡片的圆角，和 [ShiftclaTokens.CardRadius] 保持一致。 */
private const val CARD_RADIUS_DP = 24f


/** 自己写一个，免得依赖 androidx.compose.ui.util 里的重载。 */
private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/** p=0 时卡片上盖的"源卡快照"透明度；[ShiftclaDetailTokens.SNAPSHOT_FADE] 之后就完全消失。 */
private fun snapshotAlphaAt(p: Float): Float =
    (1f - p / ShiftclaDetailTokens.SNAPSHOT_FADE).coerceIn(0f, 1f)

/**
 * 整卡透明度。**只在收起时**做收尾淡出：p 掉到 [ShiftclaDetailTokens.CARD_TAIL_FADE] 以下时
 * 卡片逐渐变透明，和底下正在淡入的原卡片交叉过渡，避免"迷你详情卡"和真卡片硬切。
 * 展开时恒为 1（否则点下去那一瞬间卡片是透明的）。
 */
private fun cardAlphaAt(p: Float, closing: Boolean): Float =
    if (closing) (p / ShiftclaDetailTokens.CARD_TAIL_FADE).coerceAtMost(1f) else 1f

/**
 * 课程详情浮层（纯渲染，不含动画状态）。
 *
 * @param course         要展示的课程；null = 关闭（收起动画期间靠内部缓存续命）
 * @param progress       转场进度 0..1，由 [ShiftclaApp] 统一驱动
 * @param onDismiss      点遮罩空白处 / 按返回键时回调
 * @param sourceBounds   被点中的那张卡在窗口里的矩形（**单位是像素**，来自 boundsInWindow）；
 *                       null 时退化成原地淡入
 * @param sourceCardColor 被点中那张卡的底色，用于画 p=0 时的"源卡快照"
 */
@Composable
fun CourseDetailOverlay(
    course: Course?,
    /**
     * 转场进度。**故意用 `State<Float>` 而不是裸 `Float`** —— 裸 Float 传给组合会让
     * 整个浮层（含详情卡里的每一行文字/图标）每帧重组一次，动画就会掉帧。
     * 用 State 之后只在 `graphicsLayer { }` 的**绘制阶段**读值，组合阶段零重组。
     */
    progress: State<Float>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sourceBounds: Rect? = null,
    sourceCardColor: Color? = null,
    /**
     * 收起动画彻底结束后的信号。为 true 时整个浮层卸载（不再渲染任何一层）。
     * 由宿主在 `progress` 动画跑完后置 true，避免"透明浮层"和原卡淡回撞在同一帧。
     */
    dismissed: Boolean = false,
) {
    BackHandler(enabled = course != null) { onDismiss() }

    // 收起动画期间 course 已经是 null，缓存最后一张牌，免得内容先消失
    var lastCourse by remember { mutableStateOf(course) }
    if (course != null) lastCourse = course

    val shown = lastCourse
    if (shown == null || dismissed) return

    val src = sourceBounds?.takeIf { it.width > 0f && it.height > 0f }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // 卡片是居中的，目标矩形可以直接算出来，不必等 onGloballyPositioned ——
        // 那个回调发生在 layout 阶段、首帧绘制时还拿不到，会让动画第一帧闪一下全尺寸卡片。
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // ---------- 遮罩：随 p 淡入，点它才关闭 ----------
            Box(
                Modifier
                    .fillMaxSize()
                    .background(ShiftclaDetailTokens.Scrim)
                    .graphicsLayer { alpha = progress.value }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )

            CourseDetailCard(
                course = shown,
                transform = {
                    // 跑在图层更新阶段：size 就是卡片本体的像素尺寸，不需要任何布局回调。
                    val p = progress.value
                    val tw = size.width
                    val th = size.height
                    if (src != null && tw > 0f && th > 0f) {
                        val sx = lerp(src.width / tw, 1f, p)
                        val sy = lerp(src.height / th, 1f, p)
                        // 以左上角为原点做缩放 + 平移，数学最直观
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = sx
                        scaleY = sy
                        translationX = lerp(src.left - (boxW - tw) / 2f, 0f, p)
                        translationY = lerp(src.top - (boxH - th) / 2f, 0f, p)
                        // 视觉圆角从被点卡片的 24dp 长到 28dp；图层整体被缩放，
                        // 形状半径要先除以 scale 再交给图层裁剪，缩完才正好是目标值。
                        val apparent = lerp(CARD_RADIUS_DP, ShiftclaDetailTokens.CardRadius.value, p)
                        shape = RoundedCornerShape((apparent / sx).dp)
                    }
                },
                // closing = course == null：只要课程已经被置空，就是在收起路径上做收尾淡出
                cardAlpha = { cardAlphaAt(progress.value, closing = course == null) },
                snapshotColor = sourceCardColor,
                snapshotAlpha = { snapshotAlphaAt(progress.value) },
                // 卡片自己吃掉点击：点卡内不会关闭浮层（不要涟漪）
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            )
        }
    }
}

/**
 * 居中的白色主卡片。
 *
 * 圆角、裁剪、投影、变换都走**同一个** [graphicsLayer]：[transform] 会在里面覆盖 `shape`，
 * 投影才会跟着卡片一起缩放 —— 否则卡片缩小、投影还留在原位，会看到一圈悬空的阴影。
 *
 * @param transform     追加到图层上的变换（共享元素动画用），在 shape / shadowElevation 之后执行
 * @param cardAlpha     整卡透明度（收起收尾用）
 * @param snapshotColor 盖在卡片上的纯色快照（被点中那张卡的底色）
 * @param snapshotAlpha 快照透明度；1 = 完全是源卡的样子，0 = 完全是详情内容
 */
@Composable
fun CourseDetailCard(
    course: Course,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(ShiftclaDetailTokens.CardRadius),
    elevation: Dp = ShiftclaDetailTokens.CardElevation,
    transform: (GraphicsLayerScope.() -> Unit)? = null,
    /** 整卡透明度**在绘制阶段**求值；传 lambda 而不是 Float，避免每帧重组卡片内容 */
    cardAlpha: () -> Float = { 1f },
    snapshotColor: Color? = null,
    /** 源卡快照透明度，同样绘制阶段求值 */
    snapshotAlpha: () -> Float = { 0f },
) {
    val t = ShiftclaDetailTokens

    Box(
        modifier = modifier
            .padding(horizontal = t.CardMarginHorizontal)
            .widthIn(max = t.CardMaxWidth)
            .fillMaxWidth()
            // 图层包住卡片本体（不含左右外边距），动画里的 size 才是卡片真实尺寸。
            // 注意用 graphicsLayer 一次性把"形状 + 裁剪 + 投影 + 变换"做完：
            // 换成 Modifier.shadow 的话，投影不会跟着图形变换一起缩放。
            .graphicsLayer {
                val ca = cardAlpha()
                if (ca < 1f) alpha = ca
                shadowElevation = elevation.toPx()
                this.shape = shape
                clip = true
                transform?.invoke(this)
            }
            .background(t.CardSurface),
    ) {
        Column(Modifier.padding(horizontal = t.CardPaddingH, vertical = t.CardPaddingV)) {

            // ---------- 1. Header：左时间 / 右 Tag ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = course.timeRange,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = t.MetaSize),
                    color = ShiftclaTokens.TextTertiary,
                    maxLines = 1,
                )
                if (course.tag != null) {
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    ShiftclaTag(
                        text = course.tag,
                        tone = course.tagColor,
                        fontSize = 11.sp,
                        paddingH = 5.dp,
                        paddingV = 2.dp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 2. 主标题 ----------
            Text(
                text = course.courseName,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = t.TitleSize,
                    lineHeight = t.TitleLineHeight,
                    fontWeight = FontWeight.Bold,
                ),
                color = ShiftclaTokens.TextPrimary,
            )

            Spacer(Modifier.height(20.dp))
            DetailDivider()
            Spacer(Modifier.height(18.dp))

            // ---------- 3. 信息列表（4 行）----------
            InfoRow(Icons.Outlined.LocationOn, course.location)
            Spacer(Modifier.height(t.InfoRowGap))
            InfoRow(Icons.Outlined.Person, course.teacher)
            Spacer(Modifier.height(t.InfoRowGap))
            InfoRow(Icons.AutoMirrored.Outlined.MenuBook, course.chapter)
            Spacer(Modifier.height(t.InfoRowGap))
            InfoRow(Icons.Outlined.CalendarMonth, course.weekSlot)

            Spacer(Modifier.height(16.dp))
            DetailDivider()
            Spacer(Modifier.height(20.dp))

            // ---------- 4. 底部 AI 备注区 ----------
            if (!course.aiNote.isNullOrBlank()) {
                AiNoteRow(course.aiNote)
            }
        }

        // 源卡快照：展开初期盖在内容上，p 一过 SNAPSHOT_FADE 就完全透明。
        // 有了它，p=0 时这一坨看起来就是"被点中的那张卡"，不会白卡一闪。
        // 注意这里**不在组合期判断 alpha**（那会订阅进度、每帧重组），
        // 只要有色值就挂上，透明度交给绘制阶段。
        if (snapshotColor != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = snapshotAlpha() }
                    .background(snapshotColor),
            )
        }
    }
}

/** 1dp 极浅分隔线，撑满内容宽。 */
@Composable
private fun DetailDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ShiftclaDetailTokens.Divider),
    )
}

/** 一行信息：16dp 细线图标 + 16sp 深灰文字。行高固定 24dp，保证行距一致。 */
@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(
        Modifier.height(ShiftclaDetailTokens.InfoRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ShiftclaTokens.TextSecondary,
            modifier = Modifier.size(ShiftclaDetailTokens.InfoIconSize),
        )
        Spacer(Modifier.width(ShiftclaDetailTokens.InfoIconGap))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = ShiftclaDetailTokens.BodySize),
            color = ShiftclaTokens.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 底部 AI 区：一行两端对齐。
 *
 * 注意设计稿里这不是"一块紫底里塞个按钮"，而是**两个独立的圆角块**：
 * 左边浅紫 #F3EFFF 的文字面板，隔 12dp 是右边的按钮（实测：面板 216.7 × 62.5dp、
 * 按钮 62.9 × 61.8dp、中间留白 13dp，三者加起来正好等于内容宽 292.9dp）。
 */
@Composable
private fun AiNoteRow(note: String) {
    val t = ShiftclaDetailTokens
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(t.AiGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(t.AiPanelRadius))
                .background(t.AiPanelColor)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "AI 备注：$note",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = t.AiTextSize,
                    lineHeight = t.AiTextLineHeight,
                ),
                color = t.AiTextColor,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            Modifier
                .size(t.AiButtonSize)
                .clip(RoundedCornerShape(t.AiButtonRadius))
                .background(t.AiButtonColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = t.AiTextColor,
                modifier = Modifier.size(t.AiButtonIconSize),
            )
        }
    }
}


/* ==========================================================================
 * 宿主：主看板 + 详情浮层
 * ========================================================================== */

/**
 * 底图：清晰版 + 一张「已模糊的快照」，两者交叉淡化。
 *
 * **为什么不用「单份看板 + 半径动画」**（实测数据说了算）：
 * 把底图按 p=1/0.6/0.3/0.12/0.04/0 各渲一张、量相邻像素差分（锐度），得到
 *   半径模糊  : 4.9% / 8.2% / 13.6% / 25.2% / 66.7% / 100%
 *   交叉淡化  : 4.3% / 42.2% / 71.0% / 88.3% / 95.9% / 100%
 * 半径模糊是**饱和曲线** —— p 才 0.3 背景就糊掉 86%，于是关闭时绝大部分
 * 「变清晰」都挤在最后 30% 里发生，看起来就是「突然变清晰」。
 * 交叉淡化则是线性的（与理论值 1-p 几乎重合），所以过渡才平滑。
 *
 * **为什么模糊版能一直用同一次 RenderEffect**：
 * 它的内容是静止的（不接进度、不接点击、卡片固定透明），Compose 会跳过重组、
 * 图层内容不重录，20dp 的模糊只算一次并被缓存；每帧只改外层 alpha（纯合成）。
 *
 * @param progress 在**绘制阶段**读，动画期间零重组。
 */
@Composable
internal fun ShiftclaBackdrop(
    data: DashboardData,
    /** 底图模糊进度 0..1：由「详情浮层进度」「抽屉开合进度」「日历开合进度」取大值合成 */
    blurProgress: State<Float>,
    lifted: LiftedCard?,
    onCourseClick: (Course, Rect) -> Unit,
    onMenuClick: () -> Unit,
    onDateClick: () -> Unit = {},
    onFabClick: () -> Unit = {},
    allCourses: List<Course> = data.courses,
    selectedDate: java.time.LocalDate = java.time.LocalDate.now(),
    onSelectDate: (java.time.LocalDate) -> Unit = {},
    onBackToToday: () -> Unit = {},
) {
    val p = blurProgress.value

    // 模糊版里那张被抽走的卡**恒为全透明**：一来背景里不该再看到它，
    // 二来 alpha 恒定 → 模糊层的绘制内容完全不随动画变化 → 模糊结果能一直复用。
    val hiddenAlpha = remember { mutableFloatStateOf(0f) }
    val blurredLifted = remember(lifted?.id, hiddenAlpha) {
        lifted?.let { LiftedCard(it.id, hiddenAlpha) }
    }

    Box(Modifier.fillMaxSize().background(ShiftclaTokens.SurfaceCompact)) {
        // ---------- ① 清晰版：唯一一份可交互看板 ----------
        Box(Modifier.fillMaxSize()) {
            ShiftclaDashboard(
                data = data,
                onCourseClick = onCourseClick,
                onMenuClick = onMenuClick,
                onDateClick = onDateClick,
                onFabClick = onFabClick,
                allCourses = allCourses,
                selectedDate = selectedDate,
                onSelectDate = onSelectDate,
                // 被点中的卡随进度淡出/淡回。用 1 - p 与模糊同步：背景糊多少，
                // 这张卡就淡多少 —— 即「打开时在背景模糊的同时去掉原位置的卡片」。
                // alpha 走 State、在卡片图层绘制阶段读，组合阶段零重组。
                lifted = lifted,
            )
        }

        // ---------- ② 模糊快照：固定 20dp，只改外层 alpha ----------
        // `if (p > 0f)` 不是为了省性能，而是因为 alpha=0 的图层照样拦截指针，
        // 常驻会把底下的卡片点击全部挡掉。p 归零时移除，此刻 alpha 已平滑降到 0，无感。
        if (p > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    // ★ graphicsLayer(alpha) 在外、blur 在内：
                    //   外层只淡「已经模糊好的那张图」；内层 blur 的输入静止 → 只算一次。
                    .graphicsLayer { alpha = blurProgress.value }
                    .blur(ShiftclaDetailTokens.BackdropBlur),
            ) {
                ShiftclaDashboard(
                    data = data,
                    onCourseClick = NOOP_COURSE_CLICK,
                    revealAnimation = false,
                    lifted = blurredLifted,
                    // 模糊底图里也带着同一份切日数据：虽然隔着 blur 看不清，
                    // 但日历开着时它是背景的主体，日期错了会在圆角外露馅。
                    // onSelectDate 给 no-op：模糊底图不需要反向同步（交互全在清晰层）。
                    allCourses = allCourses,
                    selectedDate = selectedDate,
                )
            }
        }
    }
}

/** 模糊版底图不需要交互；用同一个实例，避免每次重组都产生新 lambda 破坏 skip。 */
private val NOOP_COURSE_CLICK: (Course, Rect) -> Unit = { _, _ -> }

/** 爱发电主页（赞助按钮跳转目标）。 */
private const val SPONSOR_URL = "https://afdian.com/a/DamonDDD"

/**
 * 最小宿主壳。**转场进度由这里统一持有**，因为有三样东西要一起动：
 * 浮层里的卡片、底图模糊、以及被点中那张卡的淡出。
 *
 * @param initialCourseId 直接以某门课的详情打开（预览 / 截图测试用；null = 不打开）
 * @param animateOverlay  静态预览 / 截图测试传 false：进度直接给终态
 */
@Composable
fun ShiftclaApp(
    data: DashboardData,
    modifier: Modifier = Modifier,
    initialCourseId: String? = null,
    animateOverlay: Boolean = true,
    /** 预览 / 截图测试用：直接以抽屉打开的状态渲染 */
    initialDrawerOpen: Boolean = false,
    /** 正在调 AI 解析：中央显示加载遮罩 */
    isParsing: Boolean = false,
    /** 点抽屉里的「导入教务数据」时回调（由宿主弹 ImportBottomSheet） */
    onImportClick: () -> Unit = {},
    /** 点抽屉里的「API 配置」时回调（由宿主弹 ApiKeyDialog） */
    onApiKeyClick: () -> Unit = {},
    /** 点右下角 FAB 时回调（呼出导入教务数据，与抽屉「导入教务数据」同一路径） */
    onFabClick: () -> Unit = {},
    /** 点抽屉「赞助」按钮时回调（由宿主打开爱发电链接） */
    onSponsorClick: () -> Unit = {},
    allCourses: List<Course> = data.courses,
    selectedDate: java.time.LocalDate = java.time.LocalDate.now(),
    onSelectDate: (java.time.LocalDate) -> Unit = {},
    onBackToToday: () -> Unit = {},
    /** 空态文案。由 [emptyCopyFor] 按学期阶段算出来 */
    emptyCopy: EmptyCopy = EmptyCopy(
        title = "还没有课表",
        body = "点左上角菜单 → 导入教务数据\n把教务文本丢给 AI，它会帮你排好",
        kind = EmptyKind.Sparkle,
    ),
    /**
     * 是否显示屏幕中央的空态提示。
     *
     * 只有「学期前/后」或「还没课表」才该显示居中的大提示；
     * 「学期中 + 有课表 + 这天没课」**不显示**（用户反馈：下面 AI 提示那行
     * 灰色字已经说明了，中间再冒一句既重复、又会在滑动切日时残留）。
     */
    showCenteredEmpty: Boolean = true,
) {
    var selectedCourseId by remember { mutableStateOf(initialCourseId) }
    var sourceBounds by remember { mutableStateOf<Rect?>(null) }

    // ---------- 侧边抽屉 ----------
    // 手势策略（两个看似矛盾的需求的解法）：
    //   · 去掉「右滑调出抽屉」→ 拦掉 Closed→Open 的**手势**触发
    //   · 保留「点空白关闭」  → 放行 Open→Closed（scrim 点击 / 面板拖拽关闭）
    // material3 里 gesturesEnabled 同时管这两者，没法直接用。这里用
    // confirmValueChange + allowOpen 标志精确控制：只有点汉堡按钮时才放行「打开」。
    var allowDrawerOpen by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(
        if (initialDrawerOpen) DrawerValue.Open else DrawerValue.Closed,
        confirmStateChange = { target ->
            target == DrawerValue.Closed || allowDrawerOpen
        },
    )
    val drawerScope = rememberCoroutineScope()
    var drawerSelection by remember { mutableIntStateOf(0) }
    val onMenuClick: () -> Unit = remember(drawerScope, drawerState) {
        {
            // 点汉堡：临时放行「打开」，然后恢复默认（拦截手势打开）
            allowDrawerOpen = true
            drawerScope.launch {
                drawerState.open()
                allowDrawerOpen = false
            }
        }
    }
    // 抽屉开合进度（含拖拽过程），供底图模糊使用
    val drawerFraction = rememberDrawerOpenFraction(drawerState)

    // ---------- 日历抽屉 ----------
    var showCalendar by remember { mutableStateOf(false) }

    // 底图卡片点击：remember 住，否则每次重组都是新 lambda，
    // ShiftclaDashboard 就无法 skip，动画期间会被迫逐帧重组。
    val onCardClick: (Course, Rect) -> Unit = remember {
        { course, bounds ->
            sourceBounds = bounds
            selectedCourseId = course.id
        }
    }
    // 被"抽走"的那张卡：点开时记下，等收起动画彻底结束再清
    var liftedCourseId by remember { mutableStateOf<String?>(null) }
    // 收起动画彻底结束、浮层可以卸载的信号
    var overlayDismissed by remember { mutableStateOf(initialCourseId == null) }

    val selectedCourse = selectedCourseId?.let { id -> data.courses.firstOrNull { it.id == id } }
    val open = selectedCourse != null

    // 静态预览 / 截图测试下 LocalInspectionMode 为 true：跳过动画直接给终态，
    // 否则预览里看到的是动画第一帧（一张小卡片缩在角上）。
    val animate = animateOverlay && !LocalInspectionMode.current

    // 日历开合进度：驱动底图模糊（与详情浮层 / 抽屉同一套合成机制）
    val calendarProgress = remember { Animatable(0f) }
    LaunchedEffect(showCalendar) {
        val target = if (showCalendar) 1f else 0f
        if (!animate) {
            calendarProgress.snapTo(target)
        } else {
            calendarProgress.animateTo(target, tween(220, easing = FastOutSlowInEasing))
        }
    }
    val progress = remember {
        // ⚠️ 别无条件给 1f。只有"确实要展示详情"（initialCourseId != null）才该是 1；
        // 空态 / 加载态那几张预览没有详情要开，给 1 会把底图整片糊掉 ——
        // 截图里表现为顶栏和标题全是模糊的，白白干扰验图。
        // （真机走 animate=true 分支，从 0 开始，本来就没这个问题。）
        Animatable(if (!animate && initialCourseId != null) 1f else 0f)
    }

    // 打开时先记下被"抽走"的那张卡，并撤销"已卸载"标记
    if (selectedCourse != null) {
        liftedCourseId = selectedCourse.id
        overlayDismissed = false
    }

    /*
     * 注意：清 liftedCourseId / 置 dismissed **必须放在这个 effect 里、动画跑完之后**。
     * 之前写的是 LaunchedEffect(open, p) { if (!open && p <= 0f) ... } ——
     * p 每帧都在变，等于每帧取消并重启一个协程；更要命的是 p 归零那一帧会顺手把
     * liftedCourseId 清掉，触发整个课程列表重组，正好和"模糊归零 / 浮层移除"撞在同一帧。
     */
    LaunchedEffect(open, animate) {
        if (!animate) {
            progress.snapTo(if (open) 1f else 0f)
        } else if (open) {
            progress.animateTo(1f, tween(ShiftclaDetailTokens.OPEN_MILLIS, easing = FastOutSlowInEasing))
        } else {
            progress.animateTo(0f, tween(ShiftclaDetailTokens.CLOSE_MILLIS, easing = FastOutSlowInEasing))
        }
        // 动画彻底结束后才清状态：此刻 p 已归零、浮层卡片已全透明、原卡已淡回满
        if (!open) {
            liftedCourseId = null
            overlayDismissed = true
        }
    }

    // progress 的 State 视图。给 graphicsLayer 在**绘制阶段**读 —— 这样动画每帧
    // 只更新图层属性，不会触发任何重组（重组整棵看板是掉帧的元凶）。
    val progressState = remember(progress) { progress.asState() }
    // 被抽走那张卡的透明度。derivedStateOf 里的 progress.value 同样是延迟读取。
    val liftedAlphaState = remember(progress) {
        derivedStateOf { (1f - progress.value).coerceIn(0f, 1f) }
    }
    // 只在 liftedCourseId 变化时重建（动画期间 id 不变，这个对象保持同一个引用）
    val lifted = remember(liftedCourseId, liftedAlphaState) {
        liftedCourseId?.let { LiftedCard(it, liftedAlphaState) }
    }

    val p = progress.value

    // 底图模糊由三个来源合成：详情浮层进度、抽屉开合进度、日历开合进度，取大值。
    // derivedStateOf 里的读取是延迟的，所以拖拽抽屉时不会触发任何重组。
    val backdropBlur = remember {
        derivedStateOf { maxOf(progressState.value, drawerFraction.value, calendarProgress.value) }
    }

    ShiftclaDrawerScaffold(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            ShiftclaDrawerPanel(
                selectedIndex = drawerSelection,
                onSelect = { index ->
                    drawerSelection = index
                    drawerScope.launch { drawerState.close() }
                    // 第 0 项 = 导入教务数据：收起抽屉后弹输入面板
                    when (index) {
                        0 -> onImportClick()
                        1 -> onApiKeyClick()
                    }
                },
                onSponsorClick = {
                    drawerScope.launch { drawerState.close() }
                    onSponsorClick()
                },
            )
        },
    ) {
    Box(Modifier.fillMaxSize()) {
        ShiftclaBackdrop(
            data = data,
            blurProgress = backdropBlur,
            lifted = lifted,
            onCourseClick = onCardClick,
            onMenuClick = onMenuClick,
            // 点日期 → 从左侧展开日历抽屉
            onDateClick = { showCalendar = true },
            onFabClick = onFabClick,
            allCourses = allCourses,
            selectedDate = selectedDate,
            onSelectDate = onSelectDate,
            onBackToToday = onBackToToday,
        )

        // ---------- 空态：还没有课表 / 学期前 / 学期后 ----------
        // （「这天没课」不在这里 —— 那属于当天空课，靠 AI 提示语那行灰字说明，
        //   中间的居中大提示只给「全局性」空态，否则滑动切日时会残留。）
        if (showCenteredEmpty && data.courses.isEmpty()) {
            EmptyCoursesHint(
                copy = emptyCopy,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp),
            )
        }

        // ---------- 日历抽屉：scrim + 左侧白色面板 ----------
        // 顺序在详情浮层之前：日历和详情不会同时开，但加载遮罩必须压住一切。
        AnimatedVisibility(
            visible = showCalendar,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    // 与左抽屉同款 scrim：黑 16%
                    .background(Color.Black.copy(alpha = 0.16f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showCalendar = false },
                    ),
            )
        }
        AnimatedVisibility(
            visible = showCalendar,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(tween(200)),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            // 面板挂在左上、Header 下方：手机端宽度撑满减去右侧 40dp，
            // 平板端定宽 320dp（与抽屉"不随屏拉宽"同一原则）
            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
            val compactScreen = screenWidth < ShiftclaTokens.CompactMaxWidth
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = if (compactScreen) 58.dp else 88.dp),
            ) {
                ShiftclaCalendarPanel(
                    selectedDate = selectedDate,
                    onSelectDate = {
                        onSelectDate(it)
                        showCalendar = false
                    },
                    onBackToToday = {
                        onBackToToday()
                        showCalendar = false
                    },
                    modifier = if (compactScreen) {
                        Modifier.fillMaxWidth().padding(end = ShiftclaCalendarTokens.PhoneEndMargin)
                    } else {
                        Modifier.width(ShiftclaCalendarTokens.TabletWidth)
                    },
                )
            }
        }

        CourseDetailOverlay(
            course = selectedCourse,
            progress = progressState,
            onDismiss = { selectedCourseId = null },
            sourceBounds = sourceBounds,
            sourceCardColor = (
                selectedCourse?.cardTint
                    ?: liftedCourseId?.let { id -> data.courses.firstOrNull { it.id == id }?.cardTint }
                )?.surfaceColor(),
            dismissed = overlayDismissed,
        )

        // ---------- 加载遮罩：压在最上层，顺便吃掉所有点击 ----------
        ParsingOverlay(visible = isParsing)
    }
    }
}

/**
 * AI 解析中的加载遮罩：半透明白 + 居中一张小卡 + 转圈。
 *
 * 放在最上层是为了同时挡住底图、详情浮层和抽屉的交互 ——
 * `clickable` 挂在这儿（没有任何视觉反馈）把点击全吃掉。
 */
@Composable
private fun BoxScope.ParsingOverlay(visible: Boolean) {
    if (!visible) return
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(horizontal = 32.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                color = ShiftclaTokens.Primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "AI 正在解析…",
                style = MaterialTheme.typography.bodyMedium,
                color = ShiftclaTokens.TextSecondary,
            )
        }
    }
}

/**
 * 课表为空时的引导文案。没有按钮 —— 入口固定在左上角菜单里，保持单一入口。
 *
 * 图标按 [EmptyCopy.kind] 选：学期前用日历（"还没开始"），其余用星标（AI 相关）。
 * 图标映射放在 Composable 里而不是 [EmptyCopy] 里，是为了让文案逻辑保持纯 Kotlin、
 * 能被 JVM 单测直接调用。
 */
@Composable
internal fun EmptyCoursesHint(
    copy: EmptyCopy,
    modifier: Modifier = Modifier,
) {
    val icon = when (copy.kind) {
        EmptyKind.Calendar -> Icons.Outlined.CalendarMonth
        EmptyKind.Sparkle -> Icons.Filled.AutoAwesome
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ShiftclaTokens.Primary.copy(alpha = 0.55f),
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = copy.title,
            style = MaterialTheme.typography.titleMedium,
            color = ShiftclaTokens.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = copy.body,
            style = MaterialTheme.typography.bodyMedium,
            color = ShiftclaTokens.TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/* ==========================================================================
 * 路由层：把 ViewModel 和上面这些纯展示组件粘起来
 * ========================================================================== */

/**
 * 正式入口：ViewModel 驱动。
 *
 * 这一层只做三件事，不含任何视觉：
 *  1. 把 `courses` / `isParsing` 从 StateFlow 收成 Compose 状态；
 *  2. 去掉空列表时用不到的部分，组装成 [DashboardData] 喂给纯展示的 [ShiftclaApp]；
 *  3. 管 ImportBottomSheet 的显隐，并把提交事件转成 `viewModel.importSchedule(...)`。
 *
 * @param apiKey 调 DeepSeek 用的 Key，默认取 [DEMO_DEEPSEEK_API_KEY]
 */
@Composable
fun ShiftclaRoute(
    viewModel: ShiftclaViewModel,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val schedule by viewModel.schedule.collectAsState()
    // 已在 ViewModel 里按「选中日期 + 教学周」过滤排序好
    val courses by viewModel.courses.collectAsState()
    val isParsing by viewModel.isParsing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val savedApiKey by ApiKeyStore.apiKey.collectAsState()
    val hasAnyCourses by viewModel.hasAnyCourses.collectAsState()
    val bubbles by viewModel.bubbles.collectAsState()
    val termNote by viewModel.termNote.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()

    // 真实今天（学期阶段判断 / AI 提示词锚点）。remember 住，不在每次重组里反复取时钟。
    val today = remember { ShiftclaDate.snapshot() }
    // **选中日期**的快照：Header 日期 / 周次胶囊 / 空态分支都跟它走
    val selectedSnap = remember(selectedDate) { ShiftclaDate.snapshot(selectedDate) }
    val isToday = selectedDate == java.time.LocalDate.now()

    var showImportSheet by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    // 冷启动检测：没配 API Key 就在屏幕中央弹出配置框；已有 Key 不弹。
    // rememberSaveable 保证「这次进程里弹过一次就不再弹」（用户关掉后不会反复骚扰）；
    // 用 ApiKeyStore.isConfigured 而非 savedApiKey，语义更直接（读的是磁盘真值）。
    var promptedForKey by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!ApiKeyStore.isConfigured && !promptedForKey) {
            promptedForKey = true
            showApiKeyDialog = true
        }
    }

    // 学期结束后要一句话。key 带上 savedApiKey：用户现场配好 Key 后能立刻补上真文案，
    // 而不是一直停在兜底那句。ensureTermNote 内部幂等 + 有本地缓存，重复触发不会重复付费。
    LaunchedEffect(today.phase, savedApiKey) {
        viewModel.ensureTermNote(today)
    }

    // 出错时把消息抛给宿主（弹 Snackbar / Toast 都行），然后清掉
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            onError(it)
            viewModel.consumeError()
        }
    }

    val widthDp = LocalConfiguration.current.screenWidthDp
    // 骨架（标题 / 分组）沿用示例数据，courses 与 AI 文案换成真实数据。
    // globalSummary 同时驱动手机端顶部提示语和平板端紫色卡片。
    //
    // ⚠️ 学期外必须把 globalSummary 也掐掉：它是**上一次解析课表时**生成的总评
    // （"今天有两门核心专业课"），学期结束后还挂在顶部就自相矛盾了。
    // 阶段判断用的是**选中日期**的 phase：滑到学期外的日子，那天也确实没课可看。
    val inTerm = selectedSnap.phase == TermPhase.InTerm
    val data = remember(schedule, courses, widthDp, selectedSnap, isToday) {
        ShiftclaSampleData.forScreenWidth(widthDp).copy(
            // courses 已经在 VM 里按「选中日期 + 教学周」过滤排序过
            courses = courses,
            // 日期文案跟着选中日期走：今天 / 昨天 / 明天 / 周X
            currentDate = ShiftclaDate.currentDateLabel(selectedDate),
            dateLabel = selectedSnap.dateLabel,
            weekdayLabel = selectedSnap.weekdayLabel,
            weekLabel = selectedSnap.weekLabel,
            aiHeaderPrompt = schedule.globalSummary.takeIf { inTerm },
            aiInsightCard = schedule.globalSummary.takeIf { inTerm },
        )
    }

    ShiftclaApp(
        data = data,
        modifier = modifier,
        isParsing = isParsing,
        onImportClick = { showImportSheet = true },
        onApiKeyClick = { showApiKeyDialog = true },
        // 右下角 FAB = 导入教务数据的全局快捷入口，与左侧抽屉完全一致
        onFabClick = { showImportSheet = true },
        // 赞助 → 打开爱发电主页
        onSponsorClick = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(SPONSOR_URL))
                )
            }
        },
        // 左右滑切日 / 日历选日的数据与回调
        allCourses = schedule.courses,
        selectedDate = selectedDate,
        onSelectDate = viewModel::selectDate,
        onBackToToday = viewModel::backToToday,
        // 空态分支全在 emptyCopyFor 里（有单测钉着）。
        // isToday：滑到别的日子后，「今天没有课」要变成「这天没有课」。
        emptyCopy = emptyCopyFor(
            phase = selectedSnap.phase,
            hasAnyCourses = hasAnyCourses,
            termNote = termNote,
            isToday = isToday,
        ),
        // 居中空态只给「学期外」或「还没课表」；「这天没课」不显示居中提示
        showCenteredEmpty = !hasAnyCourses || selectedSnap.phase != TermPhase.InTerm,
    )

    if (showImportSheet) {
        ImportBottomSheet(
            onDismiss = { showImportSheet = false },
            onSubmit = { text, images -> viewModel.importSchedule(text, images) },
            bubbles = bubbles,
        )
    }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            initialKey = savedApiKey,
            onDismiss = { showApiKeyDialog = false },
            onSave = { key ->
                ApiKeyStore.save(key)
                showApiKeyDialog = false
            },
            onClear = {
                ApiKeyStore.save("")
                showApiKeyDialog = false
            },
        )
    }
}


/* ==========================================================================
 * 详情页假数据 + @Preview
 * ========================================================================== */

/** 与两张稿子内容一致的示例课程。 */
private fun detailPreviewCourse() = Course(
    id = "detail",
    startTime = "08:00",
    endTime = "09:35",
    courseName = "线性代数",
    location = "教学楼 A-408",
    tag = "测验",
    tagColor = TagTone.Alert,
    teacher = "张明远 教授",
    chapter = "第七章 特征值与特征向量",
    weekSlot = "第 10 教学周 · 周四 第 1-2 节",
    aiNote = "今天有随堂测验，复习第六章行列式的性质",
)

@Preview(name = "D01 · 手机详情 (402×874)", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
internal fun PreviewDetailPhone() {
    MaterialTheme(typography = ShiftclaTypography) {
        ShiftclaApp(data = ShiftclaSampleData.forScreenWidth(402), initialCourseId = "c2")
    }
}

@Preview(name = "D02 · 折叠屏展开详情 (740×1000)", widthDp = 740, heightDp = 1000, showBackground = true)
@Composable
internal fun PreviewDetailFoldable() {
    MaterialTheme(typography = ShiftclaTypography) {
        ShiftclaApp(data = ShiftclaSampleData.forScreenWidth(740), initialCourseId = "t1")
    }
}

@Preview(name = "D03 · 平板详情 (1280×800)", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
internal fun PreviewDetailTablet() {
    MaterialTheme(typography = ShiftclaTypography) {
        ShiftclaApp(data = ShiftclaSampleData.forScreenWidth(1280), initialCourseId = "t1")
    }
}

@Preview(name = "D04 · 只有卡片（量尺寸用）", widthDp = 402, heightDp = 460, showBackground = true)
@Composable
internal fun PreviewDetailCardOnly() {
    MaterialTheme(typography = ShiftclaTypography) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFFF4F1F8)),
            contentAlignment = Alignment.Center,
        ) {
            CourseDetailCard(course = detailPreviewCourse())
        }
    }
}
