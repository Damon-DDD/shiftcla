//
//  ContentView.swift
//  Shiftcla
//
//  主视图。对应 2026-10-23 新 Figma 稿（Duo 专用）：
//
//  · 右侧竖向工具栏 SideRail：☰ 菜单 / 📖 今日 / 📅 日历 / 竖排「回到当天」/ ✦ 星标 FAB(右下角)
//  · 手机外屏（<700pt）：单列卡片 + 底部「今天共 N 门课程 / HH:mm 结束」
//  · Duo 内屏横向 / 平板（≥700pt）：上午 ‖ 下午 左右双栏，AI INSIGHT 卡落在下午栏末尾
//    底部「上午 X 门 · 下午 Y 门 / HH:mm 结束」
//  · 课程卡右上角：正在上的课显示「进行中」
//
//  液态玻璃按用户决策暂缓，浮动层用白卡 + 阴影 + 模糊。
//

import SwiftUI

struct ContentView: View {
    @State private var viewModel = ShiftclaViewModel()
    @State private var showDrawer = false
    @State private var showImport = false
    @State private var showApiKey = false
    @State private var showTermEdit = false
    @State private var showCalendar = false
    @State private var showAIChat = false
    @Namespace private var glassNS   // 日历按钮 ↔ 日历卡片的液态形变
    @State private var showDetail: Course? = nil
    @State private var debugUITriggered = false

    private var debugTarget: String {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-ui"), i + 1 < args.count else { return "" }
        return args[i + 1]
    }

    /// 形态判定：Duo 内外屏按精确点尺寸识别，其余走宽度断点。
    /// - duoInner：890×626（横向展开内屏）/ 626×890（竖）→ 上午‖下午 双栏
    /// - duoCover：382×556 概念外屏 → 单列
    /// - tablet：iPad（≥700pt）→ 安卓平板式：上午/下午分区 + 4 列 + AI 卡
    /// - phone：普通 iPhone → 2 列网格
    private enum Kind { case duoInner, duoCover, tablet, phone }

    /// 物理屏幕尺寸（点）。**不受安全区影响**，是判定机型形态的可靠依据。
    /// Duo：外屏 466×678、内屏竖 626×890、内屏横 890×626（均为 @3x 原生除以 3）。
    private var screenSize: CGSize {
        if let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene }).first {
            return scene.screen.bounds.size
        }
        return UIScreen.main.bounds.size
    }

    private func kind(for s: CGSize) -> Kind {
        let sw = screenSize.width, sh = screenSize.height
        let minSide = min(sw, sh), maxSide = max(sw, sh)

        #if DEBUG
        // 调试：`-forceDuoInner` 强制按 Duo 内屏布局渲染（用于在 iPad 上预检双栏）
        if ProcessInfo.processInfo.arguments.contains("-forceDuoInner") { return .duoInner }
        #endif

        // 1) Duo 用物理屏识别。数据来自设备类型 plist（权威）：
        //    外屏 screenID1 = 1398×2034 @3x → 466×678
        //    内屏 screenID3 = 2007×2853 @3x → 669×951
        //    这两个值与其他机型（iPhone ≤440、iPad ≥744）不重叠
        if abs(minSide - 466) <= 14 || abs(maxSide - 466) <= 14 { return .duoCover }
        if abs(minSide - 669) <= 14 || abs(maxSide - 669) <= 14 { return .duoInner }
        if abs(minSide - 951) <= 14 || abs(maxSide - 951) <= 14 { return .duoInner }

        // 2) 其余按可用宽度
        if s.width >= 700 { return .tablet }
        return .phone
    }

    /// 顶部内缩：Duo 外屏的时钟画在挖孔下方（靠下），安全区却是 0，
    /// 因此按形态给固定值，保证标题和右侧导轨都落在状态栏之下。
    private func topInset(_ k: Kind, _ geo: GeometryProxy) -> CGFloat {
        switch k {
        case .duoCover: return 64
        case .duoInner: return max(geo.safeAreaInsets.top, 22)
        default: return geo.safeAreaInsets.top
        }
    }

    var body: some View {
        GeometryReader { geo in
            let width = geo.size.width
            let k = kind(for: geo.size)

            ZStack {
                ShiftclaColor.surfaceCompact.ignoresSafeArea()

                HStack(spacing: 0) {
                    // ---- 主内容 ----
                    // Duo：底部统计固定在屏幕底部（Figma 稿如此），不随内容滚动
                    if k == .duoInner || k == .duoCover {
                        // 统计栏跟随内容排在最后（不固定），避免遮挡课程卡
                        ScrollView {
                            VStack(alignment: .leading, spacing: 16) {
                                header(showMenu: false, showCount: k == .duoInner)
                                content(kind: k, width: width)
                                footer(twoPane: k == .duoInner)
                                    .padding(.top, 4)
                            }
                            .padding(.leading, 24)
                            .padding(.trailing, SideRail.width + 30)
                            .padding(.top, 8)
                            .padding(.bottom, 10)
                        }
                    } else {
                        ScrollView {
                            VStack(alignment: .leading, spacing: 16) {
                                header(showMenu: true, showCount: true)
                                content(kind: k, width: width)
                                footer(twoPane: false)
                            }
                            .padding(.leading, 24)
                            .padding(.trailing, 8)
                            .padding(.top, 8)
                            .padding(.bottom, 12)
                        }
                    }

                }
                // ZStack 会按子视图理想宽度收缩 → 必须显式铺满，否则导轨被拖到内容右边
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                // Duo 内屏横向左右各 42pt 安全区，容器只有 867pt：
                // 横向穿透安全区，导轨才能真正贴到屏幕右边缘
                .ignoresSafeArea(.container, edges: .horizontal)
                // ⚠️ GeometryReader 不会自动避让安全区，必须手动内缩，
                // 否则标题和右侧导轨会顶进状态栏（与时间/挖孔重叠）
                .padding(.top, topInset(k, geo))
                .padding(.bottom, geo.safeAreaInsets.bottom)
                // 手机 / 平板：右下角星标 FAB（Duo 用导轨里的 FAB）
                if k == .phone || k == .tablet {
                    VStack {
                        Spacer()
                        HStack {
                            Spacer()
                            Button { showImport = true } label: {
                                Image(systemName: "sparkles")
                                    .font(.system(size: 21, weight: .semibold))
                                    .foregroundColor(.white)
                                    .frame(width: 54, height: 54)
                                    .background(ShiftclaColor.brandPurple)
                                    .clipShape(Circle())
                                    .shadow(color: ShiftclaColor.brandPurple.opacity(0.35), radius: 10, y: 4)
                            }
                        }
                    }
                    .padding(.trailing, k == .tablet ? 40 : 22)
                    .padding(.bottom, k == .tablet ? 40 : 26)
                }
            }
            // 导轨挂在最外层 ZStack 上（该层含全屏背景），才能真正贴屏幕右边缘
            .overlay(alignment: .trailing) {
                if k == .duoInner || k == .duoCover {
                    SideRail(
                        todaySelected: true,
                        onMenu: { showDrawer = true },
                        onToday: { withAnimation(.spring(response: 0.32, dampingFraction: 0.85)) { viewModel.backToToday() } },
                        onCalendar: { withAnimation(.spring(response: 0.42, dampingFraction: 0.82)) { showCalendar = true } },
                        onImport: { showImport = true },
                        onAIChat: { showAIChat = true },
                        glassNS: glassNS,
                        isCalendarOpen: showCalendar,
                        onTermEdit: { showTermEdit = true }
                    )
                    .padding(.top, k == .duoCover ? 150 : 114)   // 导轨顶端绝对位置：外屏 150 / 内屏 114
                    .padding(.trailing, 22)   // 与 WiFi 图标中心对齐
                }
            }
            .overlay {
                if let course = showDetail {
                    CourseDetailOverlay(course: course) { showDetail = nil }
                        .transition(.opacity)
                }
            }
            // 月历：贴着右侧按钮、向左展开的玻璃卡片（从小图标长出）
            .overlay {
                if showCalendar {
                    ZStack(alignment: .topTrailing) {
                    // 全屏透明背景：点任何空白处关闭月历
                    Color.clear
                        .contentShape(Rectangle())
                        .ignoresSafeArea()
                        .onTapGesture {
                            withAnimation(.spring(response: 0.38, dampingFraction: 0.88)) {
                                showCalendar = false
                            }
                        }

                    ShiftclaCalendarView(
                        selectedDate: viewModel.selectedDate,
                        onSelect: { date in
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                                viewModel.selectDate(date)
                            }
                            withAnimation(.spring(response: 0.38, dampingFraction: 0.88)) {
                                showCalendar = false
                            }
                        },
                        onClose: {
                            withAnimation(.spring(response: 0.38, dampingFraction: 0.88)) {
                                showCalendar = false
                            }
                        }
                    )
                    .frame(width: min(geo.size.width * 0.78, 520), height: geo.size.height * 0.50)
                    .glassCard(cornerRadius: 32)
                    .shadow(color: .black.opacity(0.12), radius: 18, y: 6)
                    .padding(.trailing, SideRail.width + 26)
                    .padding(.top, 196)   // 卡片标题与日历小图标对齐
                    .transition(.scale(scale: 0.1, anchor: .trailing).combined(with: .opacity))
                    }
                }
            }
            .animation(.spring(response: 0.42, dampingFraction: 0.86), value: showCalendar)
            // 侧边栏：自绘玻璃卡片（尺寸只在外层定义，避免二次约束导致变形）
            .overlay {
                if showDrawer {
                    ZStack(alignment: .topTrailing) {
                        Color.clear
                            .contentShape(Rectangle())
                            .ignoresSafeArea()
                            .onTapGesture {
                                withAnimation(.spring(response: 0.38, dampingFraction: 0.88)) { showDrawer = false }
                            }
                        drawer
                            .frame(width: min(geo.size.width * 0.62, 360),
                                   height: min(geo.size.height * 0.52, 420),
                                   alignment: .topLeading)
                            .glassCard(cornerRadius: 32)
                            .padding(.trailing, SideRail.width + 26)   // 与日历卡片同一条竖线
                            .padding(.top, 150)
                            .transition(.opacity)
                    }
                }
            }
            .animation(.spring(response: 0.4, dampingFraction: 0.86), value: showDrawer)
            // AI 对话：自绘底部浮层（系统 sheet 在外屏会被 42pt 安全区顶开）
            .overlay(alignment: .bottom) {
                if showAIChat {
                    AIChatSheet(onClose: { withAnimation(.spring(response: 0.38, dampingFraction: 0.88)) { showAIChat = false } })
                        .frame(maxWidth: .infinity)
                        .frame(height: geo.size.height * 0.62)
                        .padding(.horizontal, 10)
                        .padding(.bottom, 8)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .animation(.spring(response: 0.4, dampingFraction: 0.86), value: showAIChat)
            .sheet(isPresented: $showImport) { ImportSheet(viewModel: viewModel) }
            .overlay {
                if showTermEdit {
                    ZStack(alignment: .topTrailing) {
                        Color.clear
                            .contentShape(Rectangle())
                            .ignoresSafeArea()
                            .onTapGesture {
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.9)) { showTermEdit = false }
                            }
                        TermEditPanel(onClose: {
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.9)) { showTermEdit = false }
                        })
                        .frame(width: min(geo.size.width * 0.62, 360))
                        .glassCard(cornerRadius: 28)
                        .padding(.trailing, SideRail.width + 26)   // 与侧边栏/日历同一条竖线
                        .padding(.top, 150)
                        .transition(.opacity)
                    }
                }
            }
            .animation(.spring(response: 0.38, dampingFraction: 0.88), value: showTermEdit)
            .sheet(isPresented: $showApiKey) {
                ApiKeySheet()
                    .presentationDetents([.height(340)])
            }
        }
        // Duo 内屏横向左右各 42pt 安全区 → GeometryReader 只有 867pt，
        // 在根层横向穿透，导轨才能贴到真正的屏幕右边缘
        .ignoresSafeArea(.container, edges: .horizontal)
        .task { await viewModel.ensureTermNote() }
    }

    // MARK: - Header（SHIFTCLA + 今日课程 + 右侧课程数）

    private func header(showMenu: Bool, showCount: Bool = true) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            if showMenu {
                Button { showDrawer = true } label: {
                    Image(systemName: "line.3.horizontal")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(width: 34, height: 34)
                        .background(ShiftclaColor.brandPurple)
                        .clipShape(Circle())
                }
            }
            Text("SHIFTCLA")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(ShiftclaColor.brandPurple)
                .kerning(1.5)

            HStack(alignment: .firstTextBaseline) {
                Text(Localized.todayCourses)
                    .font(.system(size: 26, weight: .bold))
                    .foregroundColor(ShiftclaColor.textPrimary)
                Spacer()
                if viewModel.hasAnyCourses && showCount {
                    Text(AppLanguage.isZh ? "今天共\(viewModel.todayCourses().count)门课程" : "\(viewModel.todayCourses().count) classes today")
                        .font(.system(size: 12))
                        .foregroundColor(ShiftclaColor.textTertiary)
                }
            }
        }
    }

    // MARK: - 内容

    @ViewBuilder
    private func content(kind: Kind, width: CGFloat) -> some View {
        let courses = viewModel.todayCourses()
        if courses.isEmpty {
            emptyState(kind)
        } else {
            switch kind {
            case .duoInner: twoPaneContent(courses: courses, width: width)
            case .tablet: tabletContent(courses: courses, width: width)
            case .phone: phoneGrid(courses: courses)
            case .duoCover: singleColumn(courses: courses)
            }
        }
    }

    /// iPad：与安卓平板一致 —— 上午/下午分区（每区 N 列，最多 4）+ 跨行 AI 卡
    private func tabletContent(courses: [Course], width: CGFloat) -> some View {
        let morning = courses.filter { $0.block == .morning }
        let afternoon = courses.filter { $0.block == .afternoon }
        let cols = max(1, min(4, Int((width - 96) / ShiftclaDim.minCardWidth)))

        return VStack(alignment: .leading, spacing: 24) {
            sectionBlock(en: "MORNING CLASSES", zh: L.t("上午课程", "Morning"),
                         icon: "sun.max.fill", courses: morning, columns: cols)
            sectionBlock(en: "AFTERNOON CLASSES", zh: L.t("下午课程", "Afternoon"),
                         icon: "sun.and.horizon.fill", courses: afternoon, columns: cols)
            if let s = viewModel.schedule.globalSummary, !s.isEmpty {
                aiInsightCard(s)
            }
        }
    }

    private func sectionBlock(en: String, zh: String, icon: String,
                              courses: [Course], columns: Int) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(ShiftclaColor.sectionAfternoon)
                Text(en)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(ShiftclaColor.textSecondary)
                    .kerning(0.5)
                Text("/ \(zh)")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(ShiftclaColor.textSecondary)
            }

            if courses.isEmpty {
                Text(Localized.noClassInSection)
                    .font(.system(size: 12))
                    .foregroundColor(ShiftclaColor.textTertiary)
            } else {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 20), count: columns),
                          spacing: 20) {
                    ForEach(courses) { course in
                        CourseCard(course: course, inProgress: isInProgress(course)) {
                            showDetail = course
                        }
                    }
                }
            }
        }
    }

    /// 普通 iPhone：2 列网格
    private func phoneGrid(courses: [Course]) -> some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12),
                            GridItem(.flexible(), spacing: 12)], spacing: 12) {
            ForEach(courses) { course in
                CourseCard(course: course, inProgress: isInProgress(course)) {
                    showDetail = course
                }
            }
        }
    }

    /// 外屏 / 窄屏：单列卡片
    private func singleColumn(courses: [Course]) -> some View {
        VStack(spacing: 14) {
            ForEach(courses) { course in
                CourseCard(course: course, inProgress: isInProgress(course)) {
                    showDetail = course
                }
            }
        }
    }

    /// 内屏横向 / 平板：上午 ‖ 下午 双栏，AI 卡放下午栏末尾
    private func twoPaneContent(courses: [Course], width: CGFloat) -> some View {
        let morning = courses.filter { $0.block == .morning }
        let afternoon = courses.filter { $0.block == .afternoon }

        return HStack(alignment: .top, spacing: 28) {
            column(title: L.t("上午课程", "Morning"), en: "MORNING", icon: "sun.max.fill", courses: morning, tail: nil)
            column(title: L.t("下午课程", "Afternoon"), en: "AFTERNOON", icon: "sun.and.horizon.fill",
                   courses: afternoon, tail: viewModel.schedule.globalSummary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// 一栏：分区标题 + 竖排卡片 + 可选尾部（AI INSIGHT 卡）
    private func column(title: String, en: String, icon: String,
                        courses: [Course], tail: String?) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(ShiftclaColor.textSecondary)
                Text(title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(ShiftclaColor.textSecondary)
                Text(en)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(ShiftclaColor.textTertiary)
                    .kerning(0.5)
            }

            ForEach(courses) { course in
                CourseCard(course: course, inProgress: isInProgress(course)) {
                    showDetail = course
                }
            }

            if courses.isEmpty {
                Text(Localized.noClassInSection)
                    .font(.system(size: 12))
                    .foregroundColor(ShiftclaColor.textTertiary)
            }

            if let tail, !tail.isEmpty {
                aiInsightCard(tail)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// AI INSIGHT 卡：淡紫底 + ✦ 标签 + 正文
    private func aiInsightCard(_ text: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 5) {
                Image(systemName: "sparkles")
                    .font(.system(size: 10, weight: .semibold))
                Text(L.t("AI INSIGHT / 今日建议", "AI INSIGHT / Today's tip"))
                    .font(.system(size: 10, weight: .bold))
                    .kerning(0.3)
            }
            .foregroundColor(ShiftclaColor.primary)
            Text(text)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(ShiftclaColor.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ShiftclaColor.aiInsightSurface.opacity(0.55))
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    // MARK: - 底部统计行

    @ViewBuilder
    private func footer(twoPane: Bool) -> some View {
        let courses = viewModel.todayCourses()
        if !courses.isEmpty {
            let morningCount = courses.filter { $0.block == .morning }.count
            let afternoonCount = courses.filter { $0.block == .afternoon }.count
            let end = courses.map { $0.endTime }.max() ?? ""

            VStack(spacing: 10) {
                Divider().background(ShiftclaColor.cardOutline)
                HStack {
                    Text(twoPane
                         ? (AppLanguage.isZh ? "上午 \(morningCount)门 · 下午 \(afternoonCount)门" : "\(morningCount) morning · \(afternoonCount) afternoon")
                         : (AppLanguage.isZh ? "今天共\(courses.count)门课程" : "\(courses.count) classes today"))
                        .font(.system(size: 11))
                        .foregroundColor(ShiftclaColor.textTertiary)
                    Spacer()
                    if !end.isEmpty {
                        Text(AppLanguage.isZh ? "\(end) 结束" : "Ends \(end)")
                            .font(.system(size: 11))
                            .foregroundColor(ShiftclaColor.textTertiary)
                    }
                }
            }
            .padding(.top, 6)
        }
    }

    // MARK: - 进行中判断

    /// 当前时刻落在该课程时间内 → 卡片右上角显示「进行中」
    private func isInProgress(_ course: Course) -> Bool {
        let cal = Calendar.current
        let now = Date()
        let nowMin = cal.component(.hour, from: now) * 60 + cal.component(.minute, from: now)
        func toMin(_ s: String) -> Int? {
            let p = s.split(separator: ":")
            guard p.count == 2, let h = Int(p[0]), let m = Int(p[1]) else { return nil }
            return h * 60 + m
        }
        guard let s = toMin(course.startTime), let e = toMin(course.endTime) else { return false }
        return nowMin >= s && nowMin < e
    }

    // MARK: - 空态

    private func emptyState(_ kind: Kind) -> some View {
        VStack(spacing: 8) {
            Spacer(minLength: 40)
            Image(systemName: "sparkles")
                .font(.system(size: 26))
                .foregroundColor(ShiftclaColor.primary)
            Text(emptyTitle)
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(ShiftclaColor.textPrimary)
            Text(emptyBody(kind))
                .font(.system(size: 13))
                .foregroundColor(ShiftclaColor.textSecondary)
                .multilineTextAlignment(.center)
            Spacer(minLength: 40)
        }
        .frame(maxWidth: .infinity)
    }

    private var emptyTitle: String {
        switch viewModel.selectedSnap.phase {
        case .beforeTerm: return L.t("学期还未开始", "Semester hasn't started")
        case .afterTerm: return L.t("学期已经过去了", "Semester is over")
        case .inTerm: return viewModel.hasAnyCourses ? L.t("今天没有课", "No classes today") : L.t("还没有课表", "No schedule yet")
        }
    }

    private func emptyBody(_ kind: Kind) -> String {
        switch viewModel.selectedSnap.phase {
        case .beforeTerm: return "新学期还没开课\n先把教务数据导进来"
        case .afterTerm: return viewModel.termNote ?? L.t("学期结束啦，先好好松口气", "Semester's done — take a breather")
        case .inTerm:
            if viewModel.hasAnyCourses { return L.t("今天没有排课，好好休息", "No classes today, enjoy the break") }
            return kind == .duoInner || kind == .duoCover
                ? L.t("点右侧导轨的 ☰ → 导入教务数据", "Tap ☰ on the right rail → import timetable")
                : L.t("点左上角菜单 → 导入教务数据", "Tap the menu at top-left → import timetable")
        }
    }

    // MARK: - 日历浮层

    private func calendarOverlay(twoPane: Bool) -> some View {
        ZStack(alignment: twoPane ? .topLeading : .center) {
            Color.black.opacity(0.18)
                .background(.ultraThinMaterial)
                .ignoresSafeArea()
                .onTapGesture { showCalendar = false }

            ShiftclaCalendarView(
                selectedDate: viewModel.selectedDate,
                onSelect: { date in
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                        viewModel.selectDate(date)
                    }
                },
                onClose: { showCalendar = false }
            )
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .shadow(color: .black.opacity(0.12), radius: 24, y: 8)
            .frame(width: twoPane ? 360 : nil)
            .padding(.horizontal, 24)
            .padding(.top, twoPane ? 60 : 0)
            .transition(.scale(scale: 0.94).combined(with: .opacity))
        }
    }

    // MARK: - 抽屉

    private var drawer: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Shiftcla")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(ShiftclaColor.textPrimary)
                .padding(.bottom, 8)

            drawerRow(icon: "square.and.arrow.down", title: Localized.importSchedule) {
                showDrawer = false
                showImport = true
            }
            drawerRow(icon: "calendar.badge.clock", title: L.t("学期设置", "Semester")) {
                showDrawer = false
                showTermEdit = true
            }
            drawerRow(icon: "gearshape", title: Localized.apiSettings) {
                showDrawer = false
                showApiKey = true
            }

            Spacer()

            Button {
                showDrawer = false
                showApiKey = true
            } label: {
                Text(L.t("赞助", "Sponsor"))
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(ShiftclaColor.brandPurple)
                    .clipShape(Capsule())
            }
            .padding(.bottom, 0)
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func drawerRow(icon: String, title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.system(size: 13))
                    .foregroundColor(ShiftclaColor.textSecondary)
                    .frame(width: 20)
                Text(title)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(ShiftclaColor.textPrimary)
                Spacer()
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(ShiftclaColor.cardOutline, lineWidth: 1))
        }
    }
}
