@file:Suppress("SpellCheckingInspection")

package com.shiftcla.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiftcla.app.data.ShiftclaDate
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/* ============================================================================
 *  ShiftclaCalendar · 左侧日历抽屉（2026-10 新稿）
 *  ---------------------------------------------------------------------------
 *  设计语言与左侧导航抽屉一致：**贴屏幕左缘、只圆右侧两角**的白色浮块，
 *  打开时底图随进度模糊。内部：
 *    ‹  2026年 10月  ›     ← 切月圆钮（浅灰紫底）
 *    [回到今日]            ← 选中日期不是今天时才出现（浅紫底）
 *    M T W T F S S         ← 周一开头
 *    6 行 42 格            ← 前后月灰显；选中日紫圆 + 白点
 *
 *  网格的纯逻辑在 [ShiftclaDate.monthGrid]（可单测），这里只管画。
 * ========================================================================== */

/** 日历面板的尺寸与色值。 */
object ShiftclaCalendarTokens {
    val PanelColor = Color(0xFFFFFFFF)

    /** 只圆右侧（贴左缘），弧度比抽屉的 32dp 略小 —— 设计稿实测 ≈20-24dp */
    val PanelShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)

    /** 手机端面板宽度 = 屏宽 - 右侧露出的模糊背景（设计稿实测右缘距屏 39dp） */
    val PhoneEndMargin = 40.dp

    /** 平板端面板定宽（不随屏幕拉宽，与抽屉同一原则） */
    val TabletWidth = 320.dp

    /** 切月圆钮：浅灰紫底，深色箭头 */
    val NavCircle = Color(0xFFF4F3F7)
    val NavCircleSize = 36.dp
    val NavIcon = Color(0xFF1C1C20)

    /** 「回到今日」chip */
    val TodayChipBg = Color(0xFFEADDFF)
    val TodayChipText = Color(0xFF5D4598)

    /** 星期表头 / 非当月日期的灰 */
    val WeekdayLabel = Color(0xFFA9A5B2)
    val OutMonthDay = Color(0xFFC2BFCA)

    /** 选中日的紫圆 —— 与 FAB / 导入按钮同一支亮紫 */
    val SelectedCircle = Color(0xFF7C4DFF)
    val SelectedDaySize = 40.dp

    /** 格子高度（6 行网格的整体高度由它决定） */
    val CellHeight = 44.dp

    val PillShape = RoundedCornerShape(percent = 50)
}

/** 星期表头。设计稿用单字母缩写、周一开头。 */
private val WEEKDAY_HEADERS = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * 日历面板本体。
 *
 * @param selectedDate 当前选中（看板正在显示）的日期
 * @param onSelectDate 点某个日期：更新选中日期（面板由宿主收起）
 * @param onBackToToday 点「回到今日」
 * @param today        真实今天（默认系统日期）。抽出参数是为了截图测试能固定，
 *   否则「今天」的紫点随真实日期漂移，基准图会不断过期。
 */
@Composable
internal fun ShiftclaCalendarPanel(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onBackToToday: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    val t = ShiftclaCalendarTokens
    // 切月是面板的局部状态：每次打开都从"选中日期所在的月份"开始
    var displayedMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }

    Column(
        modifier
            .background(t.PanelColor, t.PanelShape)
            // 左右滑动切月份（箭头之外的第二条路径）。累计水平位移过阈值就翻月。
            .pointerInput(Unit) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onHorizontalDrag = { _, delta -> drag += delta },
                    onDragEnd = {
                        if (abs(drag) > 60f) {
                            if (drag < 0) displayedMonth = displayedMonth.plusMonths(1)   // 左滑→下月
                            else displayedMonth = displayedMonth.minusMonths(1)             // 右滑→上月
                        }
                    },
                )
            }
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
    ) {
        // ---- 月份标题行：‹  2026年 10月  › ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonthNavButton(icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft) {
                displayedMonth = displayedMonth.minusMonths(1)
            }
            Text(
                text = "${displayedMonth.year}年 ${displayedMonth.monthValue}月",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                color = ShiftclaTokens.TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            MonthNavButton(icon = Icons.Filled.ChevronRight) {
                displayedMonth = displayedMonth.plusMonths(1)
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- 回到今日：只在"选中的不是今天"时出现，否则这个按钮没有意义 ----
        if (selectedDate != today) {
            Box(
                Modifier
                    .clip(t.PillShape)
                    .background(t.TodayChipBg)
                    .clickable(onClick = onBackToToday)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "回到今日",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = t.TodayChipText,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(16.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }

        // ---- 星期表头 ----
        Row(Modifier.fillMaxWidth()) {
            WEEKDAY_HEADERS.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = t.WeekdayLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // ---- 6 行 × 7 列日期网格 ----
        val cells = remember(displayedMonth) { ShiftclaDate.monthGrid(displayedMonth) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().height(t.CellHeight)) {
                week.forEach { date ->
                    CalendarDayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == displayedMonth,
                        isSelected = date == selectedDate,
                        isToday = date == today,
                        modifier = Modifier.weight(1f),
                    ) { onSelectDate(date) }
                }
            }
        }
    }
}

/** 圆形切月按钮。 */
@Composable
private fun MonthNavButton(icon: ImageVector, onClick: () -> Unit) {
    val t = ShiftclaCalendarTokens
    Box(
        Modifier
            .size(t.NavCircleSize)
            .background(t.NavCircle, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = t.NavIcon,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 单个日期格子。选中 = 紫圆白字 + 白点；今天（未选中）= 紫粗字 + 紫点；非当月 = 灰。 */
@Composable
private fun CalendarDayCell(
    date: LocalDate,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = ShiftclaCalendarTokens
    Box(modifier.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        val numberColor = when {
            isSelected -> Color.White
            inMonth -> ShiftclaTokens.TextPrimary
            else -> t.OutMonthDay
        }
        val dotColor = when {
            isSelected -> Color.White
            isToday -> t.SelectedCircle
            else -> Color.Transparent
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .then(if (isSelected) Modifier.background(t.SelectedCircle, CircleShape) else Modifier)
                    .size(t.SelectedDaySize),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    color = numberColor,
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                )
            }
            // 圆点：选中日在紫圆里是白点，"今天但没选中"是紫点，其余透明占位
            Box(
                Modifier
                    .padding(top = 1.dp)
                    .size(4.dp)
                    .background(dotColor, CircleShape),
            )
        }
    }
}
