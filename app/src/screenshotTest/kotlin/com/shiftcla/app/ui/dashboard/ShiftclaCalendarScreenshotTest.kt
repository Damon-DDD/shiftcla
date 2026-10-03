package com.shiftcla.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import java.time.LocalDate

/**
 * 日历抽屉的离屏渲染验收。
 *
 * 面板按真机打开时的摆位渲染：贴左缘、Header 下方、手机端右侧留 40dp 露出模糊背景。
 * 背景 [ShiftclaTokens.SurfaceCompact] 上再压一层灰白卡片的暗示太费事，直接用
 * 接近模糊后的底色，重点验收**面板本身**的几何与配色。
 *
 * 选中日期固定为 2026-10-24（和设计稿同一天，方便逐像素比对）；面板内部
 * 「切月」是局部状态，每次打开都从选中日期所在月份开始 —— 截图因此稳定。
 */
class ShiftclaCalendarScreenshotTest {

    private val selected = LocalDate.of(2026, 10, 24)
    // 固定"今天"：否则日历里的今天紫点随真实日期漂移，基准图会不断过期。
    private val today = LocalDate.of(2026, 10, 24)

    @Composable
    private fun CalendarScreen(phone: Boolean) {
        MaterialTheme(typography = ShiftclaTypography) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFFEFECF3))   // 模拟模糊后的底图（比纯白略灰）
                    .statusBarsPadding()
                    .padding(top = if (phone) 58.dp else 88.dp),
            ) {
                ShiftclaCalendarPanel(
                    selectedDate = selected,
                    onSelectDate = {},
                    onBackToToday = {},
                    today = today,
                    modifier = if (phone) {
                        Modifier.fillMaxWidth().padding(end = ShiftclaCalendarTokens.PhoneEndMargin)
                    } else {
                        Modifier.width(ShiftclaCalendarTokens.TabletWidth)
                    },
                )
            }
        }
    }

    @PreviewTest
    @Preview(name = "calendar-phone", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun calendarOnPhone() = CalendarScreen(phone = true)

    @PreviewTest
    @Preview(name = "calendar-tablet", widthDp = 1280, heightDp = 800, showBackground = true)
    @Composable
    fun calendarOnTablet() = CalendarScreen(phone = false)
}
