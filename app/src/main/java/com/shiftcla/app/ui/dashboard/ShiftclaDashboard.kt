@file:Suppress("SpellCheckingInspection")
// 上面这条：本文件注释里有大量十六进制色值（F3EFFF / D0BCFF…）和字体名（Noto），
// 是设计规格说明，不是拼写错误，关掉这个文件的拼写检查免得刷满 Problems 面板。

package com.shiftcla.app.ui.dashboard

import kotlinx.serialization.Serializable
import com.shiftcla.app.data.ShiftclaDate
import com.shiftcla.app.data.TermPhase
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor

/* ============================================================================
 *  Shiftcla · 主看板 (Dashboard)
 *  ---------------------------------------------------------------------------
 *  响应式策略
 *    · Compact  (可用宽度 < 600dp，普通手机 402dp)  → 2 列错落瀑布流
 *    · Expanded (可用宽度 ≥ 600dp，折叠屏内屏 / 平板 1280dp) → 4 列瀑布流
 *                                                              + 分区标题
 *                                                              + 跨 2 列的 AI 卡片
 *
 *  尺寸 / 色值全部来自两张设计稿的像素实测（Figma 帧 402×874 @86%、1280×800 @77%），
 *  实测推导过程见文件末尾「设计规格实测表」。
 * ========================================================================== */


/* ==========================================================================
 * 1. 底层数据结构 (Data Class)
 * ========================================================================== */

/** 课程所属时段。Expanded 布局据此把课程分到左右两个半区。 */
@Serializable
enum class CourseBlock { Morning, Afternoon }

/**
 * 卡片底色。手机端三种卡片交替出现，平板端统一用 [Wide]。
 *
 *  · [Lavender] `#F3EFFF` 淡紫
 *  · [Neutral]  `#F4F3F7` 中性灰紫
 *  · [Outlined] `#FFFFFF` + 1dp 描边（"空心"卡）
 *  · [Wide]     `#F7F2FA` 平板端统一底色
 */
@Serializable
enum class CardTint { Lavender, Neutral, Outlined, Wide }

/**
 * 共享元素动画期间"被抽走"的那张卡。
 *
 * 关键在于 [alpha] 是 **State 而不是裸 Float**：
 * 卡片在 `graphicsLayer { }` 里（绘制阶段）才读它，于是动画每帧改 alpha 只会更新图层属性，
 * **不会让整棵课程列表重组**。之前用 `liftedCardAlpha: Float` 的时候，这个参数每帧都在变，
 * ShiftclaDashboard 无法 skip，两份看板（清晰版 + 模糊版）被逐帧重组，
 * 掉帧正好发生在动画收尾 —— 表现出来就是"关闭最后一下从模糊突然变清晰"。
 */
class LiftedCard(val id: String, val alpha: State<Float>)

/** Tag 配色档位。[Course.tag] 为 null 时不显示 Tag。 */
@Serializable
enum class TagTone { Alert, Accent }

/** 卡片底色对应的实际颜色。共享元素动画要拿它画"被点卡片"的快照。 */
fun CardTint.surfaceColor(): Color = when (this) {
    CardTint.Lavender -> ShiftclaTokens.CardLavender
    CardTint.Neutral -> ShiftclaTokens.CardNeutral
    CardTint.Outlined -> ShiftclaTokens.CardWhite
    CardTint.Wide -> ShiftclaTokens.CardWide
}

/**
 * 单个课程。
 *
 * @param id         稳定唯一键（LazyGrid 的 key，进场动画靠它复用）
 * @param startTime  开始时间，如 `"08:00"`
 * @param endTime    结束时间，如 `"09:35"`
 * @param courseName 课程名，如 `"数据结构"`
 * @param location   上课地点，如 `"实验室 B-205"`
 * @param tag        右上角小标签文案，如 `"测验"` / `"复习课"` / `"线上"`；null = 不显示
 * @param tagColor   Tag 配色：Alert = 粉红底，Accent = 淡紫底
 * @param cardTint   卡片底色（Compact 布局生效）
 * @param block      上午 / 下午（Expanded 布局生效）
 * @param teacher    任课教师，如 `"张明远 教授"`（详情页用）
 * @param chapter    章节内容，如 `"第七章 特征值与特征向量"`（详情页用）
 * @param weekSlot   教学周次与节次，如 `"第 10 教学周 · 周四 第 1-2 节"`（详情页用）
 * @param aiNote     AI 备注，如 `"今天有随堂测验，复习第六章行列式的性质"`（详情页用）
 * @param dayOfWeek  星期几，1=周一 … 7=周日（教务原始数据里必须有，看板按日期取课）
 * @param startWeek  教学周起始（含），1 = 第 1 教学周
 * @param endWeek    教学周结束（含）
 * @param weekParity 单双周：`"odd"` 单周 / `"even"` 双周 / null = 每周都有
 *
 * 可直接被 kotlinx.serialization 反序列化（供 [DeepSeekEngine] 解析 AI 输出）：
 * 除了 AI 一定会给的 8 个字段外，其余**全部有默认值**，所以 AI 少给字段、或多给字段
 * （配合 `ignoreUnknownKeys = true`）都不会抛异常。
 * [id] 也有默认值 —— AI 不负责编 id，由引擎在解析后补上。
 */
@Serializable
data class Course(
    val id: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val courseName: String = "",
    val location: String = "",
    val tag: String? = null,
    val tagColor: TagTone = TagTone.Accent,
    val cardTint: CardTint = CardTint.Lavender,
    val block: CourseBlock = CourseBlock.Morning,
    val teacher: String = "",
    val chapter: String = "",
    val weekSlot: String = "",
    val aiNote: String? = null,
    val dayOfWeek: Int = 1,
    /**
     * 教学周次。**看板按"当前教学周"筛选就靠这三个字段。**
     *
     * 默认 1..20 且不分单双周 = "覆盖全学期"，也就是"周次信息缺失时当它每周都上"——
     * 这是安全默认值：宁可多显示，也不要因为模型没填就整门课消失。
     */
    val startWeek: Int = 1,
    val endWeek: Int = 20,
    val weekParity: String? = null,
) {
    /** 卡片左上角的 `"08:00 - 09:35"` */
    val timeRange: String get() = "$startTime - $endTime"

    /** 这门课在第 [week] 教学周上不上。单双周、周次区间都在这里判。 */
    fun matchesWeek(week: Int): Boolean {
        if (week !in startWeek..endWeek) return false
        return when (weekParity?.trim()?.lowercase()) {
            "odd", "单", "单周" -> week % 2 == 1
            "even", "双", "双周" -> week % 2 == 0
            else -> true
        }
    }
}

/**
 * 看板数据。
 *
 * @param currentDate     右上角日期 / 状态标识，如 `"10月24日 · 今天"`
 * @param appName         品牌名（手机端右上角小字 / 平板端大标题）
 * @param weekLabel       平板端右上角周次胶囊，如 `"Week 10"`；null = 不显示
 * @param aiHeaderPrompt  手机端标题下方那行紫色 AI 提示语；null = 不显示
 * @param aiInsightCard   大屏专属建议卡片文案；Compact 时传 null
 */
data class DashboardData(
    val currentDate: String,
    val appName: String = "Shiftcla",
    val weekLabel: String? = null,
    /** 平板端日期胶囊左半："Apr 18"（粗体） */
    val dateLabel: String = "Apr 18",
    /** 平板端日期胶囊右半："Thursday" */
    val weekdayLabel: String = "Thursday",
    val aiHeaderPrompt: String? = null,
    val aiInsightCard: String? = null,
    val morningSectionTitle: String = "MORNING CLASSES",
    val morningSectionSubtitle: String = "上午课程",
    val afternoonSectionTitle: String = "AFTERNOON CLASSES",
    val afternoonSectionSubtitle: String = "下午课程",
    val courses: List<Course> = emptyList(),
) {
    val morningCourses: List<Course> get() = courses.filter { it.block == CourseBlock.Morning }
    val afternoonCourses: List<Course> get() = courses.filter { it.block == CourseBlock.Afternoon }
}


/* ==========================================================================
 * 2. 设计令牌（截图实测值，已从显示器 ICC 转到 sRGB）
 * ========================================================================== */

object ShiftclaTokens {

    // —— 全局背景 ——
    /** 手机端背景（Figma 右侧面板标注 #FFFBFE） */
    val SurfaceCompact = Color(0xFFFFFBFE)

    /** 平板端背景 */
    val SurfaceExpanded = Color(0xFFFAFAFD)

    // —— 卡片 ——
    val CardLavender = Color(0xFFF3EFFF)
    val CardNeutral = Color(0xFFF4F3F7)
    val CardWhite = Color(0xFFFFFFFF)
    val CardOutline = Color(0xFFE6E0E9)
    val CardWide = Color(0xFFF7F2FA)

    // —— 主色 ——
    val Primary = Color(0xFF6750A4)
    val PrimaryContainer = Color(0xFFEADDFF)
    val OnPrimaryContainer = Color(0xFF21005D)

    /**
     * 新稿（2026-10）的 Header 紫：汉堡按钮底色 + "SHIFTCLA" 品牌字。
     * 两张 Figma 截图（手机/平板）实测同一支 #5D4598，比 [Primary] 深一档。
     */
    val BrandPurple = Color(0xFF5D4598)

    // —— AI ——
    val AiPromptText = Color(0xFF6750A4)
    val AiInsightSurface = Color(0xFFD0BCFF)
    val AiInsightOnSurface = Color(0xFF22005D)
    val AiInsightBadge = Color(0xFFF6F0FF)

    // —— Tag ——
    val TagAlertBg = Color(0xFFFFE2E2)
    val TagAlertFg = Color(0xFF9F2627)
    val TagAccentBg = Color(0xFFEADDFF)
    val TagAccentFg = Color(0xFF3B1B71)

    // —— 文字 ——
    val TextPrimary = Color(0xFF1C1C20)     // 课程名 / 大标题
    val TextSecondary = Color(0xFF49454F)   // 地点 / 日期 / 品牌小字
    val TextTertiary = Color(0xFF6B6772)    // 卡片左上角时间

    // —— 分区标记 ——
    val SectionMorning = Color(0xFF2F7D31)   // 上午图标（绿）
    val SectionAfternoon = Color(0xFFE65200) // 下午图标（橙）
    val SectionRule = Color(0xFFF64FBE)      // 下午分区前的短横线（品红）

    // —— 圆角 / 尺寸（dp） ——
    val CardRadius = 24.dp
    val TagRadius = 5.dp
    val FabSize = 56.dp
    val FabRadius = 20.dp
    /** FAB 边距：手机 右 24 / 下 32，平板 右 48 / 下 48（实测） */
    val CompactFabEnd = 24.dp
    val CompactFabBottom = 32.dp
    val ExpandedFabEnd = 48.dp
    val ExpandedFabBottom = 48.dp
    /** 列表底部留白，避免最后一行卡片被 FAB 压住 */
    val GridBottomPadding = 112.dp

    /** 网格外距与间距（实测：手机 16 / 12，平板 48 / 28） */
    val CompactGridPadding = 16.dp
    val CompactGridSpacing = 12.dp
    val ExpandedGridPadding = 48.dp
    val ExpandedGridSpacing = 28.dp

    /** 宽度断点：< 600dp 走 Compact（"今日课程"大标题那套） */
    val CompactMaxWidth = 600.dp

    /**
     * 卡片宽度硬下限。低于这个值，卡里 15sp 的 "08:00 - 09:35" 和
     * "第一教学楼 A-301" 就会被压成省略号 —— 这是决定列数的唯一依据。
     *
     * 实测各宽度下的可用宽度（时间 + 8dp + Tag 约需 130dp，地点约 125dp，两侧内距 48dp）：
     *   176dp 卡片 → 内容区 128dp，刚好够放完整的时间+Tag 和地点。
     */
    val MinCardWidth = 176.dp
}

/**
 * 某个窗口宽度下该用多大的外距 / 间距 / 最多几列。
 *
 * 三档，边界都取整到设计稿用过的数字：
 *  · < 1000dp 用 16/12（手机稿的节奏），>= 1000dp 用 48/28（平板稿的节奏）
 *  · 列数上限：手机那套封顶 2 列（"今日课程"布局），大屏封顶 4 列（平板稿就是 4 列）
 */
private data class GridSpec(
    val pagePadding: Dp,
    val spacing: Dp,
    val minCardWidth: Dp,
    val maxColumns: Int,
)

private fun gridSpecFor(widthDp: Dp): GridSpec = when {
    widthDp >= 1000.dp -> GridSpec(48.dp, 28.dp, ShiftclaTokens.MinCardWidth, maxColumns = 4)
    widthDp >= ShiftclaTokens.CompactMaxWidth -> GridSpec(20.dp, 16.dp, ShiftclaTokens.MinCardWidth, maxColumns = 4)
    else -> GridSpec(16.dp, 12.dp, ShiftclaTokens.MinCardWidth, maxColumns = 2)
}

/**
 * 由"可用宽度"反推能放几列 —— 保证任何窗口尺寸下卡片都不窄于 [GridSpec.minCardWidth]。
 *
 * n = floor((可用宽 + 间距) / (最小卡宽 + 间距))，再夹到 [1, maxColumns]。
 * 实测校验：402dp→2 列(179dp) ／ 740dp→3 列(223dp) ／ 1280dp→4 列(275dp，与平板稿完全一致)。
 */
private fun columnCountFor(widthDp: Dp, spec: GridSpec): Int {
    val usable = (widthDp - spec.pagePadding * 2).value
    val unit = (spec.minCardWidth + spec.spacing).value
    if (unit <= 0f) return 1
    val n = floor((usable + spec.spacing.value) / unit).toInt()
    return n.coerceIn(1, spec.maxColumns)
}

/* ==========================================================================
 * 3.5 日期切换体系：Pager 页 ↔ 日期 的双向映射
 * --------------------------------------------------------------------------
 * HorizontalPager 的页码是 0..N，日期是 LocalDate —— 中间用「以今天为锚点的
 * 天数偏移」换算。锚点页 [PAGE_CENTER] = 今天，向左一页 = 昨天，向右一页 = 明天。
 * ±5 万天（≈±137 年）实际永远滑不到头，等效无限翻页。
 * ========================================================================== */

private const val PAGE_CENTER = 50_000
private const val PAGE_COUNT = PAGE_CENTER * 2 + 1

/** Pager 页码 → 日期。 */
internal fun pageToDate(page: Int, today: LocalDate): LocalDate =
    today.plusDays((page - PAGE_CENTER).toLong())

/** 日期 → Pager 页码（夹在有效范围内，防御性）。 */
internal fun dateToPage(date: LocalDate, today: LocalDate): Int =
    (PAGE_CENTER + ChronoUnit.DAYS.between(today, date)).toInt().coerceIn(0, PAGE_COUNT - 1)

/**
 * 「回到当天课程」悬浮按钮：**左下角悬浮**（距左 24dp / 距底 40dp，Figma 实测），
 * 深紫底白字（与 FAB 同一支 BrandPurple），只在选中的日期不是今天时出现。
 *
 * ⚠️ 阴影的实现方式（用户录屏抓过两次跳变）：
 *
 * **不要用 `Modifier.shadow` + 动画插拔**。`shadow` 有自己独立的阴影 RenderNode，
 * 节点随动画插入/移除时，阴影会以错误边界重建一帧 —— 真机上表现为
 * "阴影和按钮本体脱节、卡一下"（`graphicsLayer{alpha}` + `shadow` 的两段式更明显）。
 *
 * 这里改成：**节点常驻 + 单个 `graphicsLayer` 同时承载 alpha 与 shadowElevation**。
 * alpha 和阴影在同一个 RenderNode 上，是同一份图层属性，淡入淡出时物理上不可能脱节；
 * 不渲染时用 `clickable(enabled=false)` 放行触摸，而不是移除节点。
 */
@Composable
private fun BackToTodayFloating(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        alpha.animateTo(if (visible) 1f else 0f, tween(200))
    }
    val elevationPx = with(LocalDensity.current) { 6.dp.toPx() }
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .graphicsLayer {
                this.alpha = alpha.value
                this.shape = shape
                this.shadowElevation = elevationPx
                this.clip = true
            }
            .background(ShiftclaTokens.BrandPurple)
            .clickable(enabled = visible, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Text(
            text = "回到当天课程",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 在 M3 默认字阶上按设计稿实测微调。
 *
 * [Typography.titleLarge] **保持 M3 默认 22sp / Bold** —— 课程名用的就是它。
 * （手机稿实测 20sp、平板稿实测 22sp；按需求统一到 titleLarge(22sp)，
 *   手机端课程名会比原稿宽约 8dp，单行卡高 122dp 完全一致，双行卡高 150dp 对 144dp。）
 */
val ShiftclaTypography: Typography = Typography().let { base ->
    base.copy(
        // "今日课程"：实测 4 个汉字墨迹宽 94.2dp → 24sp
        headlineSmall = base.headlineSmall.copy(
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
        ),
        // 时间 / 地点 / 日期："实验室 B-205" 实测墨迹 90.7dp → 14sp
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        // 分区标题 "MORNING CLASSES / 上午课程"：实测墨迹高 14.4dp → 16sp
        titleSmall = base.titleSmall.copy(
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
        ),
        // "SHIFTCLA" 品牌小字
        labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
        // Tag 文案：实测手机端胶囊内 2 字占 21.1dp → 10.5sp
        labelSmall = base.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
    )
}

/**
 * 卡片在两档布局下的度量。都是"卡内相对坐标"实测值：
 *
 * 手机稿（卡高 122dp）：时间 14sp，行高 20 → paddingTop 16 / gap1 8 / gap2 12 / paddingBottom 18
 * 平板稿（卡高 155dp）：时间 15sp，行高 20 → paddingTop 20 / gap1 24 / gap2 21 / paddingBottom 22
 */
private data class CardMetrics(
    val horizontalPadding: Dp,
    val verticalPaddingTop: Dp,
    val verticalPaddingBottom: Dp,
    val gapTimeToTitle: Dp,
    val gapTitleToLocation: Dp,
    val metaFontSize: TextUnit,
    val tagFontSize: TextUnit,
    val tagPaddingH: Dp,
    val tagPaddingV: Dp,
)

private fun cardMetrics(compact: Boolean) = if (compact) {
    CardMetrics(
        horizontalPadding = 18.dp,
        verticalPaddingTop = 16.dp,
        verticalPaddingBottom = 18.dp,
        gapTimeToTitle = 8.dp,
        gapTitleToLocation = 12.dp,
        metaFontSize = 13.sp,
        tagFontSize = 10.sp,
        tagPaddingH = 4.dp,
        tagPaddingV = 2.dp,
    )
} else {
    CardMetrics(
        horizontalPadding = 24.dp,
        verticalPaddingTop = 20.dp,
        verticalPaddingBottom = 20.dp,
        gapTimeToTitle = 19.dp,
        gapTitleToLocation = 21.dp,
        metaFontSize = 15.sp,
        tagFontSize = 11.sp,
        tagPaddingH = 6.dp,
        tagPaddingV = 4.dp,
    )
}


/* ==========================================================================
 * 3. 入口：按窗口宽度算列数，Compact / Expanded 平滑切换
 * ========================================================================== */

@Composable
fun ShiftclaDashboard(
    data: DashboardData,
    modifier: Modifier = Modifier,
    revealAnimation: Boolean = true,
    onCourseClick: (Course, Rect) -> Unit = { _, _ -> },
    /** 共享元素动画期间被"抽走"的那张卡（就是点开的那张）；null = 谁都不动 */
    lifted: LiftedCard? = null,
    /** 点左上角汉堡时回调（打开侧边抽屉） */
    onMenuClick: () -> Unit = {},
    /** 点左上角日期时回调（打开日历抽屉） */
    onDateClick: () -> Unit = {},
    /** 点右下角 FAB 时回调（呼出导入教务数据弹窗，与左侧抽屉同一路径） */
    onFabClick: () -> Unit = {},
    /**
     * **全量**课表（未按日期过滤）。左右滑动时，Pager 的每一页用它自己算
     * 「这一天的课」（[todayCoursesOf]）—— 只有每页各算各的，滑动才不会闪内容。
     *
     * 默认取 [data].courses：预览 / 截图测试只传 data 时，锚点页能直接渲出样例数据；
     * 生产链路（ShiftclaRoute）必须显式传**未过滤**的全量课表。
     */
    allCourses: List<Course> = data.courses,
    /** 当前选中日期（= [data].courses 对应的那天） */
    selectedDate: LocalDate = LocalDate.now(),
    /** 日期变化回调：滑动落定 / 点「回到当天课程」都走它 */
    onSelectDate: (LocalDate) -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        DashboardBody(
            data = data,
            width = maxWidth,
            revealAnimation = revealAnimation,
            onCourseClick = onCourseClick,
            lifted = lifted,
            onMenuClick = onMenuClick,
            onDateClick = onDateClick,
            onFabClick = onFabClick,
            allCourses = allCourses,
            selectedDate = selectedDate,
            onSelectDate = onSelectDate,
        )
    }
}

/**
 * 断点判定之后的内容。
 *
 * 列数不是写死 2/4，而是由 [columnCountFor] 从"当前可用宽度 + 最小卡宽"反推，
 * 所以折叠屏展开、分屏、桌面小窗、横屏都会自己找到合适的列数。
 *
 * 想接官方断点的话，加依赖
 *   implementation("androidx.compose.material3.adaptive:adaptive:1.1.0")
 * 用 currentWindowAdaptiveInfo().windowSizeClass 换掉这里的宽度判断即可。
 */
@Composable
private fun DashboardBody(
    data: DashboardData,
    width: Dp,
    revealAnimation: Boolean,
    onCourseClick: (Course, Rect) -> Unit,
    lifted: LiftedCard?,
    onMenuClick: () -> Unit,
    onDateClick: () -> Unit,
    onFabClick: () -> Unit,
    allCourses: List<Course>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
) {
    val spec = gridSpecFor(width)
    val columns = columnCountFor(width, spec)
    val compact = width < ShiftclaTokens.CompactMaxWidth

    // FAB 滚动隐藏：向下滚出屏、向上滚回。由各页的瀑布流滚动状态驱动。
    val fabExpanded = remember { mutableStateOf(true) }

    Box(
        Modifier
            .fillMaxSize()
            // 背景先铺满整屏（含状态栏那条），再让内容避开状态栏
            .background(if (compact) ShiftclaTokens.SurfaceCompact else ShiftclaTokens.SurfaceExpanded)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        if (compact) {
            CompactDashboard(
                data, spec, columns, revealAnimation, onCourseClick, lifted, onMenuClick, onDateClick,
                allCourses, selectedDate, onSelectDate, fabExpanded,
            )
        } else {
            ExpandedDashboard(
                data, spec, columns, revealAnimation, onCourseClick, lifted, onMenuClick, onDateClick,
                allCourses, selectedDate, onSelectDate, fabExpanded,
            )
        }

        // 右下角 FAB。边距是实测值（手机 右24/下32，平板 右48/下48），
        // 再叠一层导航栏 inset，免得在手势条机型上被压住。
        // 滚动时向下滑出、向上滑回。
        AnimatedVisibility(
            visible = fabExpanded.value,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(
                    end = if (compact) ShiftclaTokens.CompactFabEnd else ShiftclaTokens.ExpandedFabEnd,
                    bottom = if (compact) ShiftclaTokens.CompactFabBottom else ShiftclaTokens.ExpandedFabBottom,
                ),
        ) {
            FloatingAiButton(onClick = onFabClick)
        }

        // 左下角「回到当天课程」：悬浮（不占布局流），深紫底白字。
        // 位置 Figma 实测：距左 24dp / 距底 40dp；FAB 在右下，互不重叠。
        BackToTodayFloating(
            visible = selectedDate != LocalDate.now(),
            onClick = { onSelectDate(LocalDate.now()) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(
                    start = if (compact) 24.dp else 40.dp,
                    bottom = if (compact) 40.dp else 48.dp,
                ),
        )
    }
}


/* ==========================================================================
 * 4. Compact（手机 · 2 列错落瀑布流）
 * ========================================================================== */

@Composable
private fun CompactDashboard(
    data: DashboardData,
    spec: GridSpec,
    columns: Int,
    reveal: Boolean,
    onCourseClick: (Course, Rect) -> Unit,
    lifted: LiftedCard?,
    onMenuClick: () -> Unit,
    onDateClick: () -> Unit,
    allCourses: List<Course>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    fabExpanded: MutableState<Boolean>,
) {
    // Pager 的锚点。remember 住：同一次进程内"今天"不变（冷启动自然重置）。
    val today = remember { LocalDate.now() }
    val pagerState = rememberPagerState(
        initialPage = dateToPage(selectedDate, today),
        pageCount = { PAGE_COUNT },
    )
    // 同步用的最新值：LaunchedEffect 的闭包里读 stale 值是经典坑
    val latestSelected by rememberUpdatedState(selectedDate)

    // 滑动落定 → 更新选中日期（Header 日期 / 空态 / 悬浮按钮都跟着变）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val date = pageToDate(page, today)
            if (date != latestSelected) onSelectDate(date)
        }
    }
    // 外部改日期（日历选日 / 回到今天）→ Pager 平滑滚过去
    LaunchedEffect(selectedDate) {
        val target = dateToPage(selectedDate, today)
        if (pagerState.settledPage != target) pagerState.animateScrollToPage(target)
    }

    Column(Modifier.fillMaxSize()) {

        // ---- 自定义 Header：左侧「紫色汉堡 + 日期」，右侧品牌字（2026-10 新稿）----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 汉堡：紫底白标。新稿实测 36×28dp 圆角矩形（比平板端的 46dp 圆小一号）
            Box(
                Modifier
                    .size(width = 36.dp, height = 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ShiftclaTokens.BrandPurple)
                    .clickable(onClick = onMenuClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "菜单",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            // 日期从右上角挪到左边、贴着汉堡；点它将来弹日历抽屉
            Text(
                text = data.currentDate,
                style = MaterialTheme.typography.bodyMedium,
                color = ShiftclaTokens.TextPrimary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onDateClick),
            )
            Spacer(Modifier.weight(1f))
            // 品牌字移到右上角：新稿实测与汉堡同一支紫
            Text(
                text = data.appName.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = ShiftclaTokens.BrandPurple,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
        }

        Spacer(Modifier.height(24.dp))

        // ---- 大标题 ----
        Text(
            text = "今日课程",
            style = MaterialTheme.typography.headlineSmall,
            color = ShiftclaTokens.TextPrimary,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(20.dp))

        // ---- 左右滑动切日：每一页各自按页码算「这一天的课」----
        // （页内自己算数据而不是全体共用一份，是"滑动不闪内容"的关键：
        //   相邻页在滑动过程中就已带着正确的课程被组合出来。）
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val pageDate = remember(page) { pageToDate(page, today) }
            val pageSnap = remember(pageDate) { ShiftclaDate.snapshot(pageDate) }
            val pageData = remember(pageDate, allCourses, data) {
                data.copy(courses = todayCoursesOf(allCourses, pageSnap))
            }
            val gridState = rememberLazyStaggeredGridState()
            // FAB 滚动隐藏：只由「当前页」驱动。下滑隐藏、上滑显示。
            LaunchedEffect(gridState) {
                var lastScroll = 0f
                snapshotFlow {
                    gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
                }.collect { (idx, off) ->
                    // 只在当前页才影响 FAB（切页时相邻页也会跑这个 effect，但要忽略）
                    if (pagerState.settledPage != page) return@collect
                    // 用「第一个可见 item 的全局偏移」近似滚动距离，比较增减判断方向
                    val now = idx * 1000f + off
                    val delta = now - lastScroll
                    lastScroll = now
                    if (delta > 0.5f) fabExpanded.value = false       // 往下滚 → 隐藏
                    else if (delta < -0.5f) fabExpanded.value = true   // 往上滚 → 显示
                }
            }
            Box(Modifier.fillMaxSize()) {
                LazyVerticalStaggeredGrid(
                    state = gridState,
                    columns = StaggeredGridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = spec.pagePadding,
                        end = spec.pagePadding,
                        bottom = ShiftclaTokens.GridBottomPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(spec.spacing),
                    verticalItemSpacing = spec.spacing,
                ) {
                    itemsIndexed(
                        items = pageData.courses,
                        key = { _, course -> course.id },
                    ) { index, course ->
                        RevealItem(index = index, enabled = reveal) {
                            CourseCard(
                                course = course,
                                tint = course.cardTint,
                                compact = true,
                                lifted = lifted?.takeIf { it.id == course.id },
                                onClick = { bounds -> onCourseClick(course, bounds) },
                            )
                        }
                    }
                }
                // ---- 「这天没有课」：跟着**页**走，滑动时随页面滑出视野，不会残留 ----
                // 条件：有课表（否则外层的「还没有课表」负责）且该日期在学期内。
                if (allCourses.isNotEmpty() && pageData.courses.isEmpty() && pageSnap.phase == TermPhase.InTerm) {
                    EmptyCoursesHint(
                        copy = EmptyCopy(
                            title = "这天没有课",
                            body = "这天没有排课，好好休息",
                            kind = EmptyKind.Sparkle,
                        ),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 40.dp),
                    )
                }
            }
        }
    }
}


/* ==========================================================================
 * 5. Expanded（平板 / 折叠屏内屏 · 4 列瀑布流）
 * --------------------------------------------------------------------------
 * 为什么不用 LazyVerticalStaggeredGrid(Fixed(4))？
 *   设计稿大屏的排布是**列优先**的，而且第 3 行有一张**只跨 2 列**的 AI 卡片。
 *   LazyVerticalStaggeredGrid 的 span 只有 SingleLane / FullLine 两种，没有
 *   "N 列跨列"；而且它的落列规则是「最短列优先」，第二次填充会把
 *   "英语听说强化" 放到第 3 列，和设计稿（第 2 列）不一致。
 *
 * 布局随宽度变三档（columns 由 [columnCountFor] 反推）：
 *   · columns >= 4  → 上午 ‖ 下午 两个半区并排，每半区 2 列（= 平板设计稿，1280dp 时卡宽 275dp）
 *   · columns 2..3  → 两个分区纵向堆叠，各自一整行（折叠屏展开态 740dp 时 3 列 × 223dp）
 *   · columns == 1  → 单列堆叠（极窄的桌面小窗）
 * ========================================================================== */

@Composable
private fun ExpandedDashboard(
    data: DashboardData,
    spec: GridSpec,
    columns: Int,
    reveal: Boolean,
    onCourseClick: (Course, Rect) -> Unit,
    lifted: LiftedCard?,
    onMenuClick: () -> Unit,
    onDateClick: () -> Unit,
    allCourses: List<Course>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    fabExpanded: MutableState<Boolean>,
) {
    // 并排只在能放下两个"每半区 2 列"时才用，否则硬拆会把卡片压到 130dp 以下。
    val sideBySide = columns >= 4
    val perSectionColumns = if (sideBySide) 2 else columns

    // 与手机端同一套 Pager 同步逻辑（见 CompactDashboard 内注释）
    val today = remember { LocalDate.now() }
    val pagerState = rememberPagerState(
        initialPage = dateToPage(selectedDate, today),
        pageCount = { PAGE_COUNT },
    )
    val latestSelected by rememberUpdatedState(selectedDate)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val date = pageToDate(page, today)
            if (date != latestSelected) onSelectDate(date)
        }
    }
    LaunchedEffect(selectedDate) {
        val target = dateToPage(selectedDate, today)
        if (pagerState.settledPage != target) pagerState.animateScrollToPage(target)
    }

    // 结构说明：Header 固定在顶部（不再随内容滚走），纵向滚动下放给 Pager 的**每一页**
    // —— 否则横向 Pager 嵌在无界高度的滚动列里，页高测量会直接坏掉。
    Column(Modifier.fillMaxSize()) {

        // ---- Header：左侧「圆形汉堡 + 日期胶囊」｜右侧品牌字（2026-10 新稿）----
        Row(
            Modifier.fillMaxWidth().padding(
                start = spec.pagePadding,
                end = spec.pagePadding,
                top = 32.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(ShiftclaTokens.BrandPurple)
                    .clickable(onClick = onMenuClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "菜单",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            // 日期从右上角挪到汉堡旁边；点它将来弹日历抽屉
            DatePill(date = data.dateLabel, weekday = data.weekdayLabel, onClick = onDateClick)
            Spacer(Modifier.weight(1f))
            Text(
                text = data.appName.uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = ShiftclaTokens.BrandPurple,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---- 左右滑动切日 ----
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val pageDate = remember(page) { pageToDate(page, today) }
            val pageSnap = remember(pageDate) { ShiftclaDate.snapshot(pageDate) }
            val pageData = remember(pageDate, allCourses, data) {
                data.copy(courses = todayCoursesOf(allCourses, pageSnap))
            }
            val scrollState = rememberScrollState()
            // FAB 滚动隐藏：同手机端，只由当前页驱动、下滑隐藏上滑显示。
            LaunchedEffect(scrollState) {
                var last = 0
                snapshotFlow { scrollState.value }.collect { v ->
                    if (pagerState.settledPage != page) return@collect
                    val delta = v - last
                    last = v
                    if (delta > 0) fabExpanded.value = false
                    else if (delta < 0) fabExpanded.value = true
                }
            }
            Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {

                if (sideBySide) {
                    // ---- 分区标题并排 ----
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = spec.pagePadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel(
                            title = pageData.morningSectionTitle,
                            subtitle = pageData.morningSectionSubtitle,
                            color = ShiftclaTokens.SectionMorning,
                            icon = SectionIcon.Sun,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(spec.spacing))
                        SectionLabel(
                            title = pageData.afternoonSectionTitle,
                            subtitle = pageData.afternoonSectionSubtitle,
                            color = ShiftclaTokens.SectionAfternoon,
                            icon = SectionIcon.Leaves,
                            leadingRule = true,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // ---- 4 列 = 两个 2 列半区并排 ----
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = spec.pagePadding),
                        horizontalArrangement = Arrangement.spacedBy(spec.spacing),
                    ) {
                        CourseColumns(
                            courses = pageData.morningCourses,
                            columns = 2,
                            spacing = spec.spacing,
                            reveal = reveal,
                            onCourseClick = onCourseClick,
                            lifted = lifted,
                            modifier = Modifier.weight(1f),
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(spec.spacing),
                        ) {
                            CourseColumns(
                                courses = pageData.afternoonCourses,
                                columns = 2,
                                spacing = spec.spacing,
                                reveal = reveal,
                                onCourseClick = onCourseClick,
                                lifted = lifted,
                            )
                            // 跨 2 列（= 整个半区宽度）的 AI 建议卡
                            if (pageData.aiInsightCard != null) {
                                RevealItem(index = 0, enabled = reveal) {
                                    AiInsightCard(text = pageData.aiInsightCard)
                                }
                            }
                        }
                    }
                } else {
                    // ---- 分区纵向堆叠：每个分区各自带标题、铺满一整行 ----
                    SectionBlock(
                        title = pageData.morningSectionTitle,
                        subtitle = pageData.morningSectionSubtitle,
                        icon = SectionIcon.Sun,
                        color = ShiftclaTokens.SectionMorning,
                        courses = pageData.morningCourses,
                        columns = perSectionColumns,
                        spec = spec,
                        reveal = reveal,
                        onCourseClick = onCourseClick,
                        lifted = lifted,
                    )

                    Spacer(Modifier.height(spec.spacing * 2))

                    SectionBlock(
                        title = pageData.afternoonSectionTitle,
                        subtitle = pageData.afternoonSectionSubtitle,
                        icon = SectionIcon.Leaves,
                        color = ShiftclaTokens.SectionAfternoon,
                        leadingRule = true,
                        courses = pageData.afternoonCourses,
                        columns = perSectionColumns,
                        spec = spec,
                        reveal = reveal,
                        onCourseClick = onCourseClick,
                        lifted = lifted,
                        // 课表为空时不给建议卡，避免空状态界面上还飘着一块紫色
                        aiInsight = pageData.aiInsightCard?.takeIf { pageData.courses.isNotEmpty() },
                    )
                }

                Spacer(Modifier.height(ShiftclaTokens.GridBottomPadding))
            }

            // ---- 「这天没有课」：跟页走，滑动时随页面滑出，不残留 ----
            if (allCourses.isNotEmpty() && pageData.courses.isEmpty() && pageSnap.phase == TermPhase.InTerm) {
                EmptyCoursesHint(
                    copy = EmptyCopy(
                        title = "这天没有课",
                        body = "这天没有排课，好好休息",
                        kind = EmptyKind.Sparkle,
                    ),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 40.dp),
                )
            }
            }
        }
    }
}

/** 一个分区：标题 + [columns] 列课程 + 可选的跨列 AI 建议卡（堆叠布局下 AI 卡占满整行）。 */
@Composable
private fun SectionBlock(
    title: String,
    subtitle: String,
    icon: SectionIcon,
    color: Color,
    courses: List<Course>,
    columns: Int,
    spec: GridSpec,
    reveal: Boolean,
    onCourseClick: (Course, Rect) -> Unit,
    lifted: LiftedCard?,
    aiInsight: String? = null,
    leadingRule: Boolean = false,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = spec.pagePadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionLabel(
            title = title,
            subtitle = subtitle,
            color = color,
            icon = icon,
            leadingRule = leadingRule,
            modifier = Modifier.fillMaxWidth(),
        )
        CourseColumns(
            courses = courses,
            columns = columns,
            spacing = spec.spacing,
            reveal = reveal,
            onCourseClick = onCourseClick,
            lifted = lifted,
        )
        if (aiInsight != null) {
            RevealItem(index = 0, enabled = reveal) {
                AiInsightCard(text = aiInsight)
            }
        }
    }
}

/**
 * 把课程按**列优先（轮转）**分到 [columns] 个等宽列里。
 *
 * 轮转分列正好复现设计稿：
 *  · 手机端 数据结构/马克思主义/大学物理/英语听说 在左列，线性代数/概率论/计算机网络/人工智能导论 在右列；
 *  · 平板端 上午半区 线性代数 → 列 1、大学物理(II) → 列 2，下午半区同理。
 */
@Composable
private fun CourseColumns(
    courses: List<Course>,
    columns: Int,
    spacing: Dp,
    reveal: Boolean,
    onCourseClick: (Course, Rect) -> Unit,
    lifted: LiftedCard?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        repeat(columns) { column ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                courses.forEachIndexed { index, course ->
                    if (index % columns == column) {
                        RevealItem(index = index, enabled = reveal) {
                            CourseCard(
                                course = course,
                                tint = CardTint.Wide,
                                compact = false,
                                lifted = lifted?.takeIf { it.id == course.id },
                                onClick = { bounds -> onCourseClick(course, bounds) },
                            )
                        }
                    }
                }
            }
        }
    }
}


/* ==========================================================================
 * 6. 组件
 * ========================================================================== */

/**
 * 课程卡片。
 *
 * @param compact `true` = 手机规格（18dp 内距），`false` = 平板规格（24dp 内距）
 */
@Composable
fun CourseCard(
    course: Course,
    tint: CardTint,
    compact: Boolean,
    modifier: Modifier = Modifier,
    /** 共享元素动画用：被"抽走"的那张卡整体淡出，免得和浮层里的卡片重影 */
    lifted: LiftedCard? = null,
    onClick: ((Rect) -> Unit)? = null,
) {
    val background = when (tint) {
        CardTint.Lavender -> ShiftclaTokens.CardLavender
        CardTint.Neutral -> ShiftclaTokens.CardNeutral
        CardTint.Outlined -> ShiftclaTokens.CardWhite
        CardTint.Wide -> ShiftclaTokens.CardWide
    }
    val metrics = cardMetrics(compact)

    // 记下自己在窗口里的矩形，点击时交给浮层做"从这张卡长出来"的共享元素动画
    var windowBounds by remember { mutableStateOf(Rect.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 注意：alpha 在**绘制阶段**才从 State 里读出来。
            // 这样动画每帧改 alpha 只会更新这个图层的属性，不会让卡片（乃至整棵列表）重组。
            .graphicsLayer { this.alpha = lifted?.alpha?.value ?: 1f }
            .onGloballyPositioned { windowBounds = it.boundsInWindow() }
            .clip(RoundedCornerShape(ShiftclaTokens.CardRadius))
            .then(if (onClick != null) Modifier.clickable { onClick(windowBounds) } else Modifier)
            .background(background)
            .then(
                if (tint == CardTint.Outlined) {
                    Modifier.border(
                        width = 1.dp,
                        color = ShiftclaTokens.CardOutline,
                        shape = RoundedCornerShape(ShiftclaTokens.CardRadius),
                    )
                } else {
                    Modifier
                }
            ),
    ) {
        Column(
            Modifier.padding(
                start = metrics.horizontalPadding,
                end = metrics.horizontalPadding,
                top = metrics.verticalPaddingTop,
                bottom = metrics.verticalPaddingBottom,
            )
        ) {

            // 第一行：左上角灰色小字时间 ｜ 右上角彩色圆角 Tag
            Row(verticalAlignment = Alignment.CenterVertically) {
                // weight(1f, fill = false)：让 Tag 先量到自己的固有宽度，
                // 时间文字再吃剩下的空间，避免"AI 推荐自习"这种长 Tag 被压到省略号。
                Text(
                    text = course.timeRange,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = metrics.metaFontSize),
                    color = ShiftclaTokens.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (course.tag != null) {
                    Spacer(Modifier.width(6.dp))
                    CourseTag(text = course.tag, tone = course.tagColor, metrics = metrics)
                }
            }

            Spacer(Modifier.height(metrics.gapTimeToTitle))

            // 中央：课程名（MaterialTheme.typography.titleLarge，Bold）
            Text(
                text = course.courseName,
                style = MaterialTheme.typography.titleLarge,
                color = ShiftclaTokens.TextPrimary,
            )

            Spacer(Modifier.height(metrics.gapTitleToLocation))

            // 左下角：定位 Icon + 教室名
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = ShiftclaTokens.TextTertiary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = course.location,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = metrics.metaFontSize),
                    color = ShiftclaTokens.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 右上角圆角小 Tag。实测手机端 29.1 × 14.0 dp、平板端 36.5 × 19.6 dp。 */
@Composable
private fun CourseTag(text: String, tone: TagTone, metrics: CardMetrics) {
    ShiftclaTag(
        text = text,
        tone = tone,
        fontSize = metrics.tagFontSize,
        paddingH = metrics.tagPaddingH,
        paddingV = metrics.tagPaddingV,
    )
}

/**
 * 圆角小 Tag 本体。详情页也用这一个，保证两处胶囊是同一种做法。
 *
 * @param fontSize  文案字号（手机 10sp / 平板 11sp / 详情页 11sp）
 * @param paddingH  左右内距（实测手机 4dp、平板 6dp）
 * @param paddingV  上下内距
 */
@Composable
fun ShiftclaTag(
    text: String,
    tone: TagTone,
    fontSize: TextUnit,
    paddingH: Dp,
    paddingV: Dp,
    modifier: Modifier = Modifier,
) {
    val background = if (tone == TagTone.Alert) ShiftclaTokens.TagAlertBg else ShiftclaTokens.TagAccentBg
    val foreground = if (tone == TagTone.Alert) ShiftclaTokens.TagAlertFg else ShiftclaTokens.TagAccentFg

    Box(
        modifier
            .clip(RoundedCornerShape(ShiftclaTokens.TagRadius))
            .background(background)
            .padding(horizontal = paddingH, vertical = paddingV),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = fontSize),
            color = foreground,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** 大屏专属 AI 建议卡：横向加宽（占满一个半区 = 2 列 + 间距）。 */
@Composable
private fun AiInsightCard(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ShiftclaTokens.CardRadius))
            .background(ShiftclaTokens.AiInsightSurface)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(ShiftclaTokens.AiInsightBadge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = ShiftclaTokens.Primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "AI INSIGHT / 今日建议",
                style = MaterialTheme.typography.labelSmall,
                color = ShiftclaTokens.Primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
            Spacer(Modifier.height(2.dp))
            // 设计稿的正文是 14sp；这段文案更长（约 39 个字符单位），
            // 13sp 在 578dp 的半区里正好一行。窄窗口下最多铺 3 行再省略，
            // 不要压成"（检查好前轮筒轴…"那种半截话。
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = ShiftclaTokens.AiInsightOnSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 分区标题左侧的图标。用枚举而不是 `@Composable (Color) -> Unit` 参数，少一层 lambda。 */
private enum class SectionIcon { Sun, Leaves }

/**
 * 分区标题：短横线 + 图标 + "MORNING CLASSES" + 紫色的 " / 上午课程"。
 *
 * 标题和副标题用同一个 [androidx.compose.ui.text.AnnotatedString] 拼接、整体省略号收尾，
 * 这样窄窗口下是"尾部省略"而不是像原来那样一个字一个字被裁掉（实测 740dp 时
 * "AFTERNOON CLASSES / 下午课" 最后一个字会被切掉一半）。
 *
 * 设计稿里只有下午那一侧带品红短横线（实测 16 × 1.3dp，位于 x 631.7dp）。
 */
@Composable
private fun SectionLabel(
    title: String,
    subtitle: String,
    color: Color,
    icon: SectionIcon,
    modifier: Modifier = Modifier,
    leadingRule: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (leadingRule) {
            Box(
                Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(ShiftclaTokens.SectionRule),
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            imageVector = when (icon) {
                SectionIcon.Sun -> Icons.Filled.WbSunny
                SectionIcon.Leaves -> Icons.Filled.Park
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = ShiftclaTokens.TextPrimary)) { append(title) }
                withStyle(SpanStyle(color = ShiftclaTokens.Primary)) { append(" / $subtitle") }
            },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * 右上角日期胶囊："Apr 18 Thursday"。
 *
 * 2026-10 新稿：整体从右上角挪到了左侧（汉堡按钮旁边），并支持点击 ——
 * 点它将从右向左弹出日历抽屉（组件待接，先留 [onClick] 回调位）。
 */
@Composable
private fun DatePill(date: String, weekday: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ShiftclaTokens.CardWhite)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.CalendarMonth,
            contentDescription = null,
            tint = ShiftclaTokens.TextSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = date,
            style = MaterialTheme.typography.bodyMedium,
            color = ShiftclaTokens.TextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = weekday,
            style = MaterialTheme.typography.bodyMedium,
            color = ShiftclaTokens.TextSecondary,
        )
    }
}

/**
 * 周次胶囊："Week 10"。实测 80.9 × 35.2 dp。
 *
 * ⚠️ 2026-10 新稿把它从 Header 里拿掉了（Header 只剩汉堡 + 日期 + 品牌字）。
 * 组件**暂时保留**：日历抽屉的 UI 还在画，周次大概率会在那里重新出现；
 * 真确定位后再决定留/删，别顺手删掉。
 */
@Composable
private fun WeekPill(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ShiftclaTokens.PrimaryContainer)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = ShiftclaTokens.OnPrimaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * 右下角 FAB：Squircle 圆角矩形 + 星标（✨）图标。实测 56dp，圆角 ≈ 20dp。
 *
 * 全局快捷入口：点击呼出「导入教务数据」弹窗（与左侧抽屉同一条路径）。
 * 按压反馈：按下 `scale=0.9`，松手用 spring 弹回；触发时给一次轻微马达震动。
 *
 * ⚠️ scale 用 `graphicsLayer`（绘制阶段）而不是 animateContentSize 之类 ——
 * 按下/松开是高频交互，组合期缩放会拖累重组。
 */
@Composable
private fun FloatingAiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    val haptic = LocalHapticFeedback.current

    // 按下→0.9，松开→spring 弹回 1f。Animatable 天然可打断，快速连点也顺滑。
    LaunchedEffect(pressed) {
        if (pressed) {
            scale.snapTo(0.9f)
        } else {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .size(ShiftclaTokens.FabSize)
            .clip(RoundedCornerShape(ShiftclaTokens.FabRadius))
            .background(ShiftclaTokens.Primary)
            .clickable(
                interactionSource = interactionSource,
                indication = null,   // 自定义 scale 反馈，不需要水波纹
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = "AI 助手",
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}


/* ==========================================================================
 * 7. 进场动画（Animation Ready）
 * --------------------------------------------------------------------------
 * 每张卡片外再包一层 AnimatedVisibility：slideInVertically + fadeIn，按 index 交错。
 *  · 静态预览（Android Studio Preview）里 LocalInspectionMode = true → 直接可见，
 *    否则预览会是一片空白。
 *  · 离屏截图测试传 revealAnimation = false 关掉它。
 *  · 想改成"进来一次就播完"，把 LaunchedEffect 里的 delay 去掉即可。
 * ========================================================================== */

private const val REVEAL_STAGGER_MILLIS = 55L

@Composable
private fun RevealItem(
    index: Int,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val skipAnimation = !enabled || LocalInspectionMode.current
    var visible by remember(skipAnimation) { mutableStateOf(skipAnimation) }

    LaunchedEffect(skipAnimation) {
        if (!skipAnimation) {
            delay(index.coerceAtMost(12) * REVEAL_STAGGER_MILLIS)
            visible = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
            initialOffsetY = { fullHeight -> fullHeight / 5 },
        ) + fadeIn(animationSpec = tween(durationMillis = 320)),
        exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight / 8 }) +
                fadeOut(animationSpec = tween(durationMillis = 150)),
    ) {
        content()
    }
}

/*
 * 如果不需要"N 列跨列"，用官方懒加载瀑布流即可（等价写法）：
 *
 * LazyVerticalStaggeredGrid(
 *     columns = StaggeredGridCells.Fixed(if (isExpanded) 4 else 2),
 *     contentPadding = PaddingValues(horizontal = 16.dp),
 *     horizontalArrangement = Arrangement.spacedBy(12.dp),
 *     verticalItemSpacing = 12.dp,
 * ) {
 *     items(courses, key = { it.id }) { course ->
 *         AnimatedVisibility(visible = shown) { CourseCard(course, course.cardTint, true) }
 *     }
 * }
 */


/* ==========================================================================
 * 8. 假数据 + @Preview
 * ========================================================================== */

object ShiftclaSampleData {

    /**
     * 按屏幕宽度挑演示数据 —— 阈值和布局用的 [ShiftclaTokens.CompactMaxWidth] 保持一致，
     * 免得出现"布局切了、数据没切"导致某个分区空白。
     *
     * 手机那套 8 门课全部算 Morning，是给不带分区标题的 2 列瀑布流看的；
     * 分区布局那套才有下午课程（和大屏 AI 建议卡）。
     */
    fun forScreenWidth(widthDp: Int): DashboardData =
        if (widthDp >= ShiftclaTokens.CompactMaxWidth.value.toInt()) tabletDashboard() else phoneDashboard()

    /** 手机端演示数据：8 门课，与手机设计稿一一对应。 */
    fun phoneDashboard() = DashboardData(
        currentDate = "10月24日 · 今天",
        aiHeaderPrompt = "今天有两门核心专业课，包含一次小测",
        courses = listOf(
            Course("c1", "08:00", "09:35", "数据结构", "实验室 B-205",
                tag = "测验", tagColor = TagTone.Alert, cardTint = CardTint.Lavender,
                teacher = "陈立功 副教授", chapter = "第六章 树与二叉树",
                weekSlot = "第 10 教学周 · 周二 第 1-2 节",
                aiNote = "今天有随堂测验，重点复习二叉树的遍历与平衡"),
            Course("c2", "08:00", "09:35", "线性代数", "教学楼 A-408",
                tag = "测验", tagColor = TagTone.Alert, cardTint = CardTint.Outlined,
                teacher = "张明远 教授", chapter = "第七章 特征值与特征向量",
                weekSlot = "第 10 教学周 · 周四 第 1-2 节",
                aiNote = "今天有随堂测验，复习第六章行列式的性质"),
            Course("c3", "10:00", "11:35", "马克思主义基本原理", "教学楼 A-301",
                cardTint = CardTint.Neutral,
                teacher = "刘文彬 教授", chapter = "第三章 人类社会及其发展规律",
                weekSlot = "第 10 教学周 · 周二 第 3-4 节",
                aiNote = "下周交读书报告，别拖到最后一天"),
            Course("c4", "11:45", "12:30", "概率论与数理统计", "自习室 B-2",
                tag = "AI推荐自习", tagColor = TagTone.Accent, cardTint = CardTint.Lavender,
                teacher = "王雪 讲师", chapter = "第四章 大数定律与中心极限定理",
                weekSlot = "第 10 教学周 · 周四 第 5 节",
                aiNote = "这周作业正确率 62%，建议把中心极限定理再刷一遍"),
            Course("c5", "14:00", "15:35", "大学物理", "理学楼 104",
                tag = "复习课", tagColor = TagTone.Accent, cardTint = CardTint.Outlined,
                teacher = "赵启山 教授", chapter = "第八章 静电场与电势",
                weekSlot = "第 10 教学周 · 周一 第 5-6 节",
                aiNote = "期中范围到第七章，实验报告本周五截止"),
            Course("c6", "15:45", "17:20", "计算机网络", "实验楼 C-101",
                cardTint = CardTint.Neutral,
                teacher = "孙雅 副教授", chapter = "第五章 运输层 · TCP 可靠传输",
                weekSlot = "第 10 教学周 · 周三 第 7-8 节",
                aiNote = "下次实验做 TCP 抓包，提前装好 Wireshark"),
            Course("c7", "18:30", "20:05", "英语听说", "外语楼 C-502",
                cardTint = CardTint.Neutral,
                teacher = "Emily Chen 讲师", chapter = "Unit 6 Academic Presentation",
                weekSlot = "第 10 教学周 · 周四 第 9-10 节",
                aiNote = "本周准备 3 分钟英文演讲，主题自选"),
            Course("c8", "20:15", "21:50", "人工智能导论", "腾讯会议",
                tag = "线上", tagColor = TagTone.Accent, cardTint = CardTint.Lavender,
                teacher = "周澈 教授", chapter = "第九章 卷积神经网络",
                weekSlot = "第 10 教学周 · 周五 第 11-12 节",
                aiNote = "线上课，记得提前 5 分钟进会议室调试麦克风"),
        ),
    )

    /** 平板端演示数据：上午 4 门打散成 2 列 + 下午 2 门 + 大屏 AI 建议卡。 */
    fun tabletDashboard() = DashboardData(
        currentDate = "Apr 18 · Thursday",
        weekLabel = "Week 10",
        dateLabel = "Apr 18",
        weekdayLabel = "Thursday",
        aiInsightCard = "下午没有课，去图书馆或者骑你的喜德盛 RF380（检查好前轮筒轴）去兜风吧 ✨",
        courses = listOf(
            Course("t1", "08:00", "09:35", "线性代数", "第一教学楼 A-301",
                tag = "测验", tagColor = TagTone.Alert, block = CourseBlock.Morning,
                teacher = "张明远 教授", chapter = "第七章 特征值与特征向量",
                weekSlot = "第 10 教学周 · 周四 第 1-2 节",
                aiNote = "今天有随堂测验，复习第六章行列式的性质"),
            Course("t2", "08:00", "09:35", "大学物理 (II)", "第二教学楼 B-102",
                tag = "复习课", block = CourseBlock.Morning,
                teacher = "赵启山 教授", chapter = "第八章 静电场与电势",
                weekSlot = "第 10 教学周 · 周一 第 5-6 节",
                aiNote = "期中范围到第七章，实验报告本周五截止"),
            Course("t3", "14:00", "15:35", "马克思主义基本原理", "第一教学楼 C-108",
                tag = "重点", block = CourseBlock.Afternoon,
                teacher = "刘文彬 教授", chapter = "第三章 人类社会及其发展规律",
                weekSlot = "第 10 教学周 · 周二 第 3-4 节",
                aiNote = "下周交读书报告，别拖到最后一天"),
            Course("t4", "16:00", "17:35", "概率论与数理统计", "理科楼 201",
                tag = "测验", tagColor = TagTone.Alert, block = CourseBlock.Afternoon,
                teacher = "王雪 讲师", chapter = "第四章 大数定律与中心极限定理",
                weekSlot = "第 10 教学周 · 周四 第 5 节",
                aiNote = "这周作业正确率 62%，建议把中心极限定理再刷一遍"),
            Course("t5", "10:10", "11:45", "数据结构与算法", "理科楼 504 机房",
                tag = "上机", block = CourseBlock.Morning,
                teacher = "陈立功 副教授", chapter = "第六章 树与二叉树",
                weekSlot = "第 10 教学周 · 周三 第 3-4 节",
                aiNote = "机房上机，记得带校园卡和 U 盘"),
            Course("t6", "10:10", "11:45", "英语听说强化", "外语综合楼 203",
                tag = "线上", block = CourseBlock.Morning,
                teacher = "Emily Chen 讲师", chapter = "Unit 6 Academic Presentation",
                weekSlot = "第 10 教学周 · 周五 第 3-4 节",
                aiNote = "线上课，记得提前 5 分钟进会议室调试麦克风"),
        ),
    )
}

@Preview(name = "01 · 手机 2 列 (402×874)", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
internal fun PreviewPhoneCompact() {
    MaterialTheme(typography = ShiftclaTypography) {
        ShiftclaDashboard(data = ShiftclaSampleData.forScreenWidth(402), revealAnimation = false)
    }
}

@Preview(name = "02 · 平板 4 列 (1280×800)", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
internal fun PreviewTabletExpanded() {
    MaterialTheme(typography = ShiftclaTypography) {
        ShiftclaDashboard(data = ShiftclaSampleData.forScreenWidth(1280), revealAnimation = false)
    }
}

// ↓ 折叠屏展开态实测宽度。这一档以前会被硬拆成两个半区（卡片只剩 140dp，文字全被省略），
//   现在按最小卡宽反推得到 3 列 × 223dp。
@Preview(name = "03 · 折叠屏展开 3 列 (740×1000)", widthDp = 740, heightDp = 1000, showBackground = true)
@Composable
internal fun PreviewFoldableUnfolded() {
    MaterialTheme(typography = ShiftclaTypography) {
        ShiftclaDashboard(data = ShiftclaSampleData.forScreenWidth(740), revealAnimation = false)
    }
}

@Preview(name = "04 · 分屏 / 小窗 2 列 (560×900)", widthDp = 560, heightDp = 900, showBackground = true)
@Composable
internal fun PreviewSplitWindow() {
    MaterialTheme(typography = ShiftclaTypography) {
        ShiftclaDashboard(data = ShiftclaSampleData.forScreenWidth(560), revealAnimation = false)
    }
}

@Preview(name = "05 · 桌面小窗 1 列 (320×800)", widthDp = 320, heightDp = 800, showBackground = true)
@Composable
internal fun PreviewNarrowWindow() {
    MaterialTheme(typography = ShiftclaTypography) {
        ShiftclaDashboard(data = ShiftclaSampleData.forScreenWidth(320), revealAnimation = false)
    }
}

@Preview(name = "06 · 平板横屏临界 2 列 (1000×700)", widthDp = 1000, heightDp = 700, showBackground = true)
@Composable
internal fun PreviewWideCritical() {
    MaterialTheme(typography = ShiftclaTypography) {
        ShiftclaDashboard(data = ShiftclaSampleData.forScreenWidth(1000), revealAnimation = false)
    }
}


/* ==========================================================================
 * 附：设计规格实测表
 * --------------------------------------------------------------------------
 * 两张 Figma 稿的缩放比用画板蓝框标定：手机 346px/402dp = 0.86、平板 981px/1280dp = 0.766
 * （与 Figma 面板显示的 86% / 77% 一致）。所有值先经 ICC→sRGB 校正再取"模式色"。
 *
 * 几何
 *   手机 402×874       间距 12/12  外距 16  卡片 179×122(单行)/144(双行)  圆角 24  FAB 56 @ 右24/下32
 *   平板 1280×800      间距 28/28  外距 48  卡片 275×155                圆角 24  FAB 56 @ 右48/下48
 *   平板午/下午两半区各 578dp 宽，间隙 28 —— 48×2 + 4×275 + 3×28 = 1280 精确闭合
 *
 * 自适应规则（列数不写死，由"可用宽度 + MinCardWidth(176dp)"反推）
 *   n = floor((宽 - 2×外距 + 间距) / (176 + 间距))，夹到 [1, maxColumns]
 *   外距/间距：< 1000dp 用 16/12（手机稿节奏），>= 1000dp 用 48/28（平板稿节奏）
 *   实测各档（脚本 verify/adaptive_check.py 自动核对）：
 *     402dp → 2 列 × 179dp      560dp → 2 列 × 258dp      320dp → 1 列 × 288dp
 *     740dp → 3 列 × 223dp（折叠屏展开实测宽度）          1280dp → 4 列 × 275dp（= 平板稿）
 *   并排 vs 堆叠：只有 ≥4 列时才把上午/下午拆成左右两个半区；
 *   2~3 列时两个分区纵向堆叠、各自带标题铺满整行 —— 否则硬拆会把卡片压到 140dp，时间和地点全被省略。
 *
 * 色板（sRGB）
 *   背景  手机 #FFFBFE ／ 平板 #FAFAFD
 *   卡片  #F3EFFF(淡紫) #F4F3F7(中性) #FFFFFF+1dp#E6E0E9(空心) ／ 平板 #F7F2FA
 *   主色  #6750A4   容器 #EADDFF   onContainer #21005D
 *   AI   提示紫 #6750A4 ／ 大屏卡 #D0BCFF + 文字 #22005D
 *   Tag  测验 #FFE2E2 / #9F2627   其它 #EADDFF / #3B1B71
 *   文字  主 #1C1C20  次 #49454F  时间 #6B6772
 *   分区  上午 #2F7D31(绿) 下午 #E65200(橙) 下午前短横线 #F64FBE
 *
 * 字号（由字墨反推）
 *   今日课程 24sp Bold ｜ 课程名 titleLarge 22sp Bold ｜ 时间/地点 手机13sp·平板15sp
 *   分区标题 16sp Bold ｜ 品牌 手机12sp·平板26sp ｜ Tag 手机10sp·平板11sp
 *
 * 已知偏差（都是物理限制或需求取舍）
 *   1. 设计用 PingFang SC / SF Pro，Android 落 Noto Sans CJK + Roboto，
 *      同字号字宽差 2~4%，卡片宽度固定时表现为文字略宽/略窄。
 *   2. 手机稿课程名实测 20sp、平板稿 22sp；按需求统一用 titleLarge(22sp)，
 *      所以手机端课程名比原稿宽约 8dp、双行卡高 150dp 对原稿 144dp。
 *   3. 手机稿的卡片时间实测 14sp，但同字号下 Roboto 数字比 SF Pro 宽，
 *      和"AI推荐自习"这种长 Tag 同排会挤到省略号，故降到 13sp。
 *   4. 平板稿的 Tag 是"空色块"，本实现按需求渲染成带字胶囊，所以比原稿宽。
 *   5. 手机画板含系统状态栏（y 0..44），预览里没有；真机上由
 *      windowInsetsPadding(WindowInsets.statusBars) 让内容避开状态栏。
 * ========================================================================== */
