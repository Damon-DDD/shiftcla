@file:Suppress("SpellCheckingInspection")

package com.shiftcla.app.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.DrawerState

/* ============================================================================
 *  ShiftclaDrawer · 悬浮式侧边导航抽屉
 *  ---------------------------------------------------------------------------
 *  实测规格（手机稿 402×874 @85.4%、平板稿 1280×800 @75.9%；ICC→sRGB 校正后取色）
 *    面板     宽 280dp（两张稿一致；不随屏幕拉宽）  高 ≈380dp（两张稿都是 ~381dp）
 *             上下各留 24dp 悬空；纯白 #FFFFFF
 *             圆角只做右侧：topEnd / bottomEnd = 32dp，左侧 0
 *    内距     左右 20dp，上 20dp，下 20dp
 *    标题     "Shiftcla" 28sp Bold #1C1C20，下面留 ~20dp 再走分隔线
 *    分隔线   1dp，撑满内容宽
 *    菜单项   高 48dp、间距 8dp；底 #F4F3F7，圆角 12dp
 *             图标 24dp 细线，「导入教务数据」= FileDownload、「API 配置」= Settings
 *    赞助按钮 46×32dp 实心药丸 #6750A4（实测 #64539E），白字 13sp，贴左下角
 *  交互：保留 Compose 原生 ModalNavigationDrawer —— 左缘拖拽、速度吸附、
 *        预测性返回都由它内置，不自己重写阻尼。
 * ========================================================================== */

/** 抽屉的尺寸与色值，全部为截图实测。 */
object ShiftclaDrawerTokens {
    val PanelColor = Color(0xFFFFFFFF)

    /** 面板宽度：两张稿量出来都是 280dp，不随屏幕变宽 */
    val PanelWidth = 280.dp

    /** 面板高度：两张稿都是 ~381dp —— 这是一块"悬浮卡片"而不是通栏抽屉 */
    val PanelHeight = 380.dp

    /** 上下悬空量（用户要求：不要贴边） */
    val PanelMarginVertical = 24.dp

    /** 只做右侧圆角，左侧切平 —— 贴在屏幕左缘 */
    val PanelShape: Shape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)

    val ContentPaddingH = 22.dp
    val ContentPaddingTop = 20.dp
    val ContentPaddingBottom = 20.dp

    // 实测两稿标题墨迹宽都是 106dp（手机/平板一致）→ 按渲染字宽标定反推 26sp
    val TitleSize = 26.sp

    val ItemHeight = 48.dp
    val ItemGap = 8.dp
    val ItemShape: Shape = RoundedCornerShape(12.dp)
    val ItemContainer = Color(0xFFF4F3F7)
    val ItemContainerSelected = Color(0xFFE7E5ED)
    val ItemIconSize = 24.dp

    val Divider = Color(0xFFE7E0EC)

    val SponsorHeight = 32.dp
    val SponsorTextSize = 13.sp
    val SponsorShape: Shape = RoundedCornerShape(percent = 50)
}

/** 抽屉里的一项。 */
private data class DrawerEntry(val label: String, val icon: ImageVector)

private val DRAWER_ENTRIES = listOf(
    DrawerEntry("导入教务数据", Icons.Outlined.FileDownload),
    DrawerEntry("API 配置", Icons.Outlined.Settings),
)

/**
 * 悬浮抽屉面板本体。
 *
 * @param selectedIndex   当前选中项；用 `-1` 表示都不选中
 * @param onSelect        点某一项时回调
 * @param onSponsorClick  点「赞助」时回调
 */
@Composable
fun ShiftclaDrawerPanel(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onSponsorClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = ShiftclaDrawerTokens

    // 顶部额外让开状态栏：App 是 enableEdgeToEdge 的，系统栏浮在内容之上，
    // 只留 24dp 的话面板标题会和状态栏的时间重叠。
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    ModalDrawerSheet(
        modifier = modifier
            // 上下悬空：不贴屏幕上下边（顶部再让开状态栏）
            .padding(
                top = t.PanelMarginVertical + statusBarTop,
                bottom = t.PanelMarginVertical,
            )
            // 固定宽 + 固定高：平板端也不许拉宽、不许拉高
            .width(t.PanelWidth)
            .height(t.PanelHeight),
        // 异形大圆角：只圆右侧
        drawerShape = t.PanelShape,
        drawerContainerColor = t.PanelColor,
        drawerTonalElevation = 0.dp,
        // 面板自己已经悬浮了，不该再吃系统栏 inset
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = t.ContentPaddingH,
                    end = t.ContentPaddingH,
                    top = t.ContentPaddingTop,
                    bottom = t.ContentPaddingBottom,
                ),
        ) {
            // ---------- Header ----------
            Text(
                text = "Shiftcla",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = t.TitleSize),
                color = ShiftclaTokens.TextPrimary,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(thickness = 1.dp, color = t.Divider)
            Spacer(Modifier.height(24.dp))

            // ---------- 菜单区 ----------
            DRAWER_ENTRIES.forEachIndexed { index, entry ->
                if (index > 0) Spacer(Modifier.height(t.ItemGap))
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            maxLines = 1,
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = null,
                            modifier = Modifier.size(t.ItemIconSize),
                        )
                    },
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    shape = t.ItemShape,
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = t.ItemContainerSelected,
                        unselectedContainerColor = t.ItemContainer,
                        selectedIconColor = ShiftclaTokens.TextPrimary,
                        unselectedIconColor = ShiftclaTokens.TextPrimary,
                        selectedTextColor = ShiftclaTokens.TextPrimary,
                        unselectedTextColor = ShiftclaTokens.TextPrimary,
                    ),
                    modifier = Modifier.height(t.ItemHeight),
                )
            }

            // ---------- Footer：把赞助推到底部 ----------
            Spacer(Modifier.weight(1f))

            Button(
                onClick = onSponsorClick,
                modifier = Modifier.height(t.SponsorHeight),
                shape = t.SponsorShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ShiftclaTokens.Primary,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
            ) {
                Text(
                    text = "赞助",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = t.SponsorTextSize),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 把主界面包进原生 [ModalNavigationDrawer]。
 *
 * 这里不自己写手势与阻尼：M3 的 ModalNavigationDrawer 已经内置了左缘向右拖拽、
 * 速度吸附、以及预测性返回，行为比手写更"跟手"。
 *
 * @param drawerProgress 抽屉开合进度 0..1（**含拖拽过程**），由 [ShiftclaApp] 用来
 *   驱动底图的模糊；`State` 形式是为了在绘制阶段读取、避免每帧重组。
 */
@Composable
fun ShiftclaDrawerScaffold(
    drawerState: DrawerState,
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        // 用户要求：去掉右滑调出抽屉，只保留汉堡按钮触发。
        // ⚠️ material3 里 `gesturesEnabled` 同时管「拖拽手势」和「scrim 点击关闭」
        // （源码 Scrim.onClose 里也判了 gesturesEnabled）。关掉它会导致点空白关不掉。
        // 所以这里**保持 true**（让 scrim 能点关），拖拽打开由下面的 confirmValueChange 拦掉。
        gesturesEnabled = true,
        // 「轻微的黑色半透明遮罩」：比 M3 默认的 32% 更淡
        scrimColor = Color.Black.copy(alpha = 0.16f),
        drawerContent = drawerContent,
        content = content,
    )
}

/**
 * 抽屉开合进度（0 = 关、1 = 全开），**拖拽过程中也跟手**。
 *
 * M3 的锚点（源码 NavigationDrawer.kt）：
 * ```
 * val calculatedClosedAnchor = -width.toFloat()
 * DraggableAnchors { Closed at -width; Open at 0f }     // 都是像素
 * ```
 * 也就是 **关闭时 offset = -面板宽，全开时 offset = 0**。所以
 * `进度 = (offset + 面板宽) / 面板宽 = 1 + offset / 面板宽`。
 *
 * ⚠️ 注意别写成 `-offset / 面板宽` —— 那样关闭态会算出 1、打开态算出 0，
 * 结果是「App 一启动底图就是满屏模糊」。
 *
 * 未测量前 `currentOffset` 是 `Float.NaN`，兜底用 `isOpen ? 1 : 0`。
 *
 * 返回 `State` 是为了让调用方在 `graphicsLayer` 的**绘制阶段**读它，
 * 拖拽时零重组。
 */
@Composable
fun rememberDrawerOpenFraction(
    drawerState: DrawerState,
    panelWidth: Dp = ShiftclaDrawerTokens.PanelWidth,
): State<Float> {
    val widthPx = with(LocalDensity.current) { panelWidth.toPx() }
    return remember(drawerState, widthPx) {
        derivedStateOf {
            val offset = drawerState.currentOffset
            if (offset.isNaN()) {
                if (drawerState.isOpen) 1f else 0f
            } else {
                (1f + offset / widthPx).coerceIn(0f, 1f)
            }
        }
    }
}
