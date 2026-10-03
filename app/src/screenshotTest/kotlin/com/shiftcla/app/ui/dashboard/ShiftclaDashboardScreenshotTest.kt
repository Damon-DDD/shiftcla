package com.shiftcla.app.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import com.shiftcla.app.R
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

/**
 * 把 @Preview 离屏渲染成 PNG：既用来和设计稿做几何比对，也用来检查各档宽度下的文字截断。
 *
 * 两个坑（AGP screenshot 插件）：
 *  1. 类名必须以 Test / Tests 结尾，否则 BUILD SUCCESSFUL 但产出 0 张图；
 *  2. 每个预览函数必须标 @PreviewTest（只能标在函数上，标类上会编译报错）。
 */
class ShiftclaDashboardScreenshotTest {

    @PreviewTest
    @Preview(name = "phone-402", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun phoneTwoColumn() {
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaDashboard(data = ShiftclaSampleData.forScreenWidth(402), revealAnimation = false)
        }
    }

    @PreviewTest
    @Preview(name = "tablet-1280", widthDp = 1280, heightDp = 800, showBackground = true)
    @Composable
    fun tabletFourColumn() {
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaDashboard(data = ShiftclaSampleData.forScreenWidth(1280), revealAnimation = false)
        }
    }

    /** 折叠屏展开态实测宽度：以前这里会被硬拆两半，卡片只剩 140dp。 */
    @PreviewTest
    @Preview(name = "foldable-740", widthDp = 740, heightDp = 1180, showBackground = true)
    @Composable
    fun foldableUnfolded() {
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaDashboard(data = ShiftclaSampleData.forScreenWidth(740), revealAnimation = false)
        }
    }

    /** 分屏 / 桌面小窗：列数应当自动降下来，而不是把卡片压窄。 */
    @PreviewTest
    @Preview(name = "window-560", widthDp = 560, heightDp = 900, showBackground = true)
    @Composable
    fun splitWindow() {
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaDashboard(data = ShiftclaSampleData.forScreenWidth(560), revealAnimation = false)
        }
    }

    @PreviewTest
    @Preview(name = "narrow-320", widthDp = 320, heightDp = 800, showBackground = true)
    @Composable
    fun narrowWindow() {
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaDashboard(data = ShiftclaSampleData.forScreenWidth(320), revealAnimation = false)
        }
    }
}

/** 课程详情浮层的离屏渲染。 */
class ShiftclaDetailScreenshotTest {

    @PreviewTest
    @Preview(name = "detail-phone-402", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun detailOnPhone() {
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaApp(data = ShiftclaSampleData.forScreenWidth(402), initialCourseId = "c2", animateOverlay = false)
        }
    }

    @PreviewTest
    @Preview(name = "detail-foldable-740", widthDp = 740, heightDp = 1000, showBackground = true)
    @Composable
    fun detailOnFoldable() {
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaApp(data = ShiftclaSampleData.forScreenWidth(740), initialCourseId = "t1", animateOverlay = false)
        }
    }

    @PreviewTest
    @Preview(name = "detail-tablet-1280", widthDp = 1280, heightDp = 800, showBackground = true)
    @Composable
    fun detailOnTablet() {
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaApp(data = ShiftclaSampleData.forScreenWidth(1280), initialCourseId = "t1", animateOverlay = false)
        }
    }

    /**
     * 转场进度钉在 p=0 的那一帧：卡片应当**精确落在被点中的那张卡上**，
     * 并且盖着源卡底色的快照（看起来就是原来那张卡，而不是一张白卡）。
     * 用来验证 scale + translation 的变换数学。
     *
     * 注意 sourceBounds 的单位是**像素**（真机上来自 boundsInWindow()），
     * 这里按 density 从 dp 换算；写死 dp 会得出"数学错了"的假结论。
     */
    @PreviewTest
    @Preview(name = "detail-p0-open", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun detailOpeningFrame() {
        MaterialTheme(typography = ShiftclaTypography) {
            val density = LocalDensity.current
            val source = with(density) {
                Rect(16.dp.toPx(), 199.5.dp.toPx(), 195.dp.toPx(), 321.5.dp.toPx())
            }
            val course = Course(
                id = "p0", startTime = "08:00", endTime = "09:35",
                courseName = "线性代数", location = "教学楼 A-408",
                tag = "测验", tagColor = TagTone.Alert,
                teacher = "张明远 教授", chapter = "第七章 特征值与特征向量",
                weekSlot = "第 10 教学周 · 周四 第 1-2 节",
                aiNote = "今天有随堂测验，复习第六章行列式的性质",
            )
            // 源卡底色用淡紫，模拟被点中的"数据结构"那种卡
            Box(Modifier.fillMaxSize().background(Color(0xFFE9E5F0))) {
                CourseDetailOverlay(
                    course = course,
                    progress = remember { mutableFloatStateOf(0f) },
                    onDismiss = {},
                    sourceBounds = source,
                    sourceCardColor = Color(0xFFF3EFFF),
                )
            }
        }
    }
}


/**
 * 转场各进度的底图快照（诊断用）。
 *
 * 目的：量出背景在 p = 1.0 / 0.6 / 0.3 / 0.12 / 0.04 / 0 时**实际的模糊程度**，
 * 验证它是一条平滑单调的曲线 —— 而不是靠肉眼看动画猜。
 * 用锐度指标（相邻像素差分的平均幅值）量化：越模糊，指标越低。
 */
class ShiftclaBlurRampScreenshotTest {

    @Composable
    private fun BackdropAt(p: Float) {
        val state = remember { mutableFloatStateOf(p) }
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaBackdrop(
                data = ShiftclaSampleData.forScreenWidth(402),
                blurProgress = state,
                lifted = null,
                onCourseClick = { _, _ -> },
                onMenuClick = {},
            )
        }
    }

    @PreviewTest
    @Preview(name = "p100", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun p100() = BackdropAt(1.0f)

    @PreviewTest
    @Preview(name = "p60", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun p60() = BackdropAt(0.6f)

    @PreviewTest
    @Preview(name = "p30", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun p30() = BackdropAt(0.3f)

    @PreviewTest
    @Preview(name = "p12", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun p12() = BackdropAt(0.12f)

    @PreviewTest
    @Preview(name = "p04", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun p04() = BackdropAt(0.04f)

    @PreviewTest
    @Preview(name = "p00", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun p00() = BackdropAt(0.0f)
}


/**
 * 新图标预览：按设计稿叠出「渐变底 + 前景层」，并额外出一张圆形遮罩版
 * —— 用来检查自适应图标在圆形启动器下会不会被裁掉（安全区 66/108）。
 */
class ShiftclaAppIconScreenshotTest {

    @Composable
    private fun IconCanvas(circleMask: Boolean) {
        val bg = Brush.linearGradient(
            colors = listOf(Color(0xFF41308C), Color(0xFF2A1F5E), Color(0xFF150E2E)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .then(if (circleMask) Modifier.clip(CircleShape) else Modifier)
                .background(bg),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @PreviewTest
    @Preview(name = "icon-square", widthDp = 108, heightDp = 108, showBackground = true)
    @Composable
    fun iconSquare() = IconCanvas(circleMask = false)

    @PreviewTest
    @Preview(name = "icon-circle", widthDp = 108, heightDp = 108, showBackground = true)
    @Composable
    fun iconCircle() = IconCanvas(circleMask = true)
}


/** 侧边导航抽屉（开启态）：手机 + 平板两档。 */
class ShiftclaDrawerScreenshotTest {

    @PreviewTest
    @Preview(name = "drawer-phone-402", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun drawerOnPhone() {
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaApp(
                data = ShiftclaSampleData.forScreenWidth(402),
                initialDrawerOpen = true,
                animateOverlay = false,
            )
        }
    }

    @PreviewTest
    @Preview(name = "drawer-tablet-1280", widthDp = 1280, heightDp = 800, showBackground = true)
    @Composable
    fun drawerOnTablet() {
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaApp(
                data = ShiftclaSampleData.forScreenWidth(1280),
                initialDrawerOpen = true,
                animateOverlay = false,
            )
        }
    }
}


/**
 * 抽屉对底图模糊的影响，**隔离测量**（不经过详情浮层）。
 *
 * 为什么不能直接用 ShiftclaApp 测：截图测试里 `LocalInspectionMode = true`，
 * 详情浮层的进度会直接给终态 1，底图本来就糊，测不出抽屉那一路。
 * 这里只把 `rememberDrawerOpenFraction` 接到 `ShiftclaBackdrop` 上，
 * 关 / 开各渲一张比锐度 —— 关闭态必须远高于开启态。
 */
class ShiftclaDrawerBlurScreenshotTest {

    @Composable
    private fun Scene(drawerOpen: Boolean) {
        val drawerState = rememberDrawerState(
            if (drawerOpen) DrawerValue.Open else DrawerValue.Closed
        )
        val fraction = rememberDrawerOpenFraction(drawerState)
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaDrawerScaffold(
                drawerState = drawerState,
                drawerContent = {
                    ShiftclaDrawerPanel(selectedIndex = 0, onSelect = {}, onSponsorClick = {})
                },
            ) {
                ShiftclaBackdrop(
                    data = ShiftclaSampleData.forScreenWidth(402),
                    blurProgress = fraction,
                    lifted = null,
                    onCourseClick = { _, _ -> },
                    onMenuClick = {},
                )
            }
        }
    }

    @PreviewTest
    @Preview(name = "closed", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun closed() = Scene(drawerOpen = false)

    @PreviewTest
    @Preview(name = "opened", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun opened() = Scene(drawerOpen = true)
}


/**
 * 业务闭环的视觉校验：输入面板 / 加载态 / 空态。
 *
 * 弹窗类（ModalBottomSheet）在 layoutlib 里不好截，所以面板内容单独抽了
 * `ImportSheetContent` 出来渲；空态和加载态直接跑真实的 ShiftclaApp。
 */
class ShiftclaImportFlowScreenshotTest {

    @PreviewTest
    @Preview(name = "import-sheet-empty", widthDp = 402, heightDp = 520, showBackground = true)
    @Composable
    fun sheetEmpty() {
        MaterialTheme(typography = ShiftclaTypography) {
            Box(Modifier.fillMaxSize().background(Color(0xFFF4F3F7))) {
                ImportSheetContent(onSubmit = { _, _ -> })
            }
        }
    }

    @PreviewTest
    @Preview(name = "import-sheet-filled", widthDp = 402, heightDp = 520, showBackground = true)
    @Composable
    fun sheetFilled() {
        MaterialTheme(typography = ShiftclaTypography) {
            Box(Modifier.fillMaxSize().background(Color(0xFFF4F3F7))) {
                ImportSheetContent(
                    onSubmit = { _, _ -> },
                    initialText = "周一 1-2 节 高等数学 王老师 A101；周三 3-4 节 大学英语 李老师 B202",
                )
            }
        }
    }

    /** 空课表 + 引导文案。 */
    @PreviewTest
    @Preview(name = "empty-state", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun emptyState() {
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaApp(
                data = ShiftclaSampleData.forScreenWidth(402).copy(courses = emptyList()),
                animateOverlay = false,
            )
        }
    }

    /** 解析中的加载遮罩。 */
    @PreviewTest
    @Preview(name = "parsing", widthDp = 402, heightDp = 874, showBackground = true)
    @Composable
    fun parsing() {
        MaterialTheme(typography = ShiftclaTypography) {
            ShiftclaApp(
                data = ShiftclaSampleData.forScreenWidth(402),
                animateOverlay = false,
                isParsing = true,
            )
        }
    }
}
