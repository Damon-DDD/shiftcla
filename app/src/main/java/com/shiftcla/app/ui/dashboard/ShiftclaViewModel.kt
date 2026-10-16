@file:Suppress("SpellCheckingInspection")

package com.shiftcla.app.ui.dashboard

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shiftcla.app.R
import com.shiftcla.app.data.ApiKeyStore
import com.shiftcla.app.data.ChatBubble
import com.shiftcla.app.data.ChatTurn
import com.shiftcla.app.data.DeepSeekEngine
import com.shiftcla.app.data.ScheduleResponse
import com.shiftcla.app.data.ShiftclaDate
import com.shiftcla.app.data.ScheduleStore
import com.shiftcla.app.data.TermNote
import com.shiftcla.app.data.TermPhase
import com.shiftcla.app.data.TermInfoStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 看板页面的状态中枢。
 *
 * 只用最朴素的三个 `MutableStateFlow`，不引入额外框架 ——
 * UI 层 `collectAsState()` 一下就能用，也方便单测直接读 `.value`。
 *
 * 数据流向（闭环）：
 * ```
 * ImportBottomSheet 输入文本
 *        ↓  importSchedule(rawText, apiKey)
 *   isParsing = true  →  界面中央转圈
 *        ↓  DeepSeekEngine.parseSchedule(...)
 *   courses = 解析结果 →  isParsing = false  →  瀑布流自动错落进场
 * ```
 */
class ShiftclaViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * 取当前语言的资源文案。
     *
     * 为什么用 [AndroidViewModel]：错误提示、对话气泡这些文案必须跟着系统语言走，
     * 而它们是在 ViewModel 里拼出来的 —— ViewModel 本身没有 Context，
     * AndroidViewModel 提供的 Application 级 Context 正好够用（生命周期同进程，不会泄漏）。
     */
    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    /**
     * AI 解析结果（含课程数组 + 全局总评）。默认空对象。
     *
     * 持有整个 [ScheduleResponse] 而不是裸 List，是因为 `globalSummary`
     * 还要驱动手机端顶部提示语和平板端紫色卡片，不能丢。
     *
     * ⚠️ 构造时就从 [ScheduleStore] 读回持久化的课表 —— 否则后台划掉 App 再打开，
     * 课表只剩内存里那份（早已随进程一起没了）。
     */
    private val _schedule = MutableStateFlow(ScheduleStore.load() ?: ScheduleResponse())
    val schedule: StateFlow<ScheduleResponse> = _schedule.asStateFlow()

    // ---------------------------------------------------------------- 日期切换体系

    /**
     * 当前选中的日期。左右滑、日历选日、回到今天，改的都是它。
     *
     * **冷启动永远回到今天**：`MutableStateFlow(LocalDate.now())` 在 ViewModel 构造时求值，
     * 而 ViewModel 随进程存活 —— 后台划掉再打开就是新进程，自然回到今天。
     *
     * ⚠️ 必须声明在 [courses] 之前：Kotlin 属性按声明顺序初始化，
     * `courses` 的 combine 里要读它。
     */
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /** 日历点选 / 滑动切日统一走这里（StateFlow 同值去重，不会触发多余重组）。 */
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    /** 回到今天。 */
    fun backToToday() {
        _selectedDate.value = LocalDate.now()
    }

    /**
     * 看板真正要渲染的课：**只保留选中日期（按星期几 + 教学周）的课，并按开始时间升序**。
     *
     * 过滤逻辑在顶层纯函数 [todayCoursesOf] 里（见 `TodayCourses.kt`），这里只负责
     * 把它接到状态流上。放在 ViewModel 而不是 UI 里，是为了让 UI 保持"给什么画什么"。
     */
    val courses: StateFlow<List<Course>> = combine(_schedule, _selectedDate) { response, date ->
        todayCoursesOf(response.courses, ShiftclaDate.snapshot(date))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 解析结果里到底有没有课（跟"今天有没有课"是两回事，空态文案要区分）。 */
    val hasAnyCourses: StateFlow<Boolean> = _schedule
        .map { it.courses.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 是否正在调用 AI 解析。UI 拿它显示加载遮罩。 */
    private val _isParsing = MutableStateFlow(false)
    val isParsing: StateFlow<Boolean> = _isParsing.asStateFlow()

    /** 送进 API 的对话上下文（assistant 轮存原始 JSON）。 */
    private val _transcript = MutableStateFlow<List<ChatTurn>>(emptyList())

    /** 展示给用户的对话气泡（人话）。 */
    private val _bubbles = MutableStateFlow<List<ChatBubble>>(emptyList())
    val bubbles: StateFlow<List<ChatBubble>> = _bubbles.asStateFlow()

    /** 最近一次失败原因；null = 无错误。UI 可以拿它弹提示。 */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * 学期结束后界面上那句 AI 文案。
     *
     * null = 还没有 AI 写的版本。UI 会退回 `R.string.empty_after_term_fallback`
     * （不能在这里塞写死的中文兜底，否则英文用户会看到中文）。
     */
    private val _termNote = MutableStateFlow<String?>(null)
    val termNote: StateFlow<String?> = _termNote.asStateFlow()

    /** 防重复请求（LaunchedEffect 可能因为重组被多次触发）。 */
    private var termNoteLoading = false

    /**
     * 确保"学期已经结束"那句话拿得到。
     *
     * 调用方是 UI 的 `LaunchedEffect`，key 是「阶段 + 是否配了 Key」，
     * 所以这个函数**必须幂等**：
     *  1. 只有 [TermPhase.AfterTerm] 才干活，其他阶段直接返回；
     *  2. 本地缓存优先 —— 一句话一学期只让 AI 写一次；
     *  3. 已在请求中就忽略；
     *  4. 没配 Key 立刻用兜底文案填充，界面不会一直空着。
     */
    fun ensureTermNote(snapshot: ShiftclaDate.Snapshot) {
        if (snapshot.phase != TermPhase.AfterTerm) return

        TermNote.cached(snapshot.termKey)?.let {
            _termNote.value = it
            return
        }
        if (termNoteLoading) return
        if (!ApiKeyStore.isConfigured) {
            // 没配 Key：保持 null，由 UI 用当前语言的兜底文案顶上
            _termNote.value = null
            return
        }

        termNoteLoading = true
        viewModelScope.launch {
            try {
                val written = DeepSeekEngine.writeTermNote(TermNote.prompt(snapshot))
                    ?.takeIf { it.isNotBlank() }
                // 只有 AI 真写出东西才落盘：兜底不缓存，
                // 这样用户之后配了 API Key 还能再试一次。
                written?.let { TermNote.save(snapshot.termKey, it) }
                _termNote.value = written
            } finally {
                termNoteLoading = false
            }
        }
    }

    /**
     * 把一段教务原始文本交给 AI 解析并灌进 [courses]。
     *
     * @param rawText 从教务系统复制的任意文本
     * @param apiKey  DeepSeek API Key
     */
    fun importSchedule(rawText: String, imageBase64s: List<String> = emptyList()) {
        // 「清除所有课程」这类指令**本地识别**，不发给 AI —— 否则模型会把它当一段
        // 教务文本去"找课程"（用户实测过）。意图判断不依赖网络、零成本、必中。
        if (isClearAllIntent(rawText)) {
            clearAllCourses()
            return
        }
        if (rawText.isBlank() && imageBase64s.isEmpty()) {
            _errorMessage.value = str(R.string.msg_empty_input)
            return
        }
        // 没配 Key 就别发请求了，直接提示
        if (!ApiKeyStore.isConfigured) {
            _errorMessage.value = str(R.string.msg_need_api_key)
            return
        }
        if (_isParsing.value) return   // 防连点：正在解析就忽略后续点击

        viewModelScope.launch {
            _isParsing.value = true
            _errorMessage.value = null
            try {
                val history = _transcript.value + ChatTurn(role = "user", content = rawText)
                _bubbles.value = _bubbles.value + ChatBubble(fromUser = true, text = rawText)

                // 已经有课表 → 带上它，让模型做增量修改；首轮则为 null
                val currentJson = _schedule.value
                    .takeIf { it.courses.isNotEmpty() }
                    ?.let { DeepSeekEngine.encodeSchedule(it) }

                // 不传 key，让 engine 自己去本地存储读（单一数据源）
                val parsed = DeepSeekEngine.chat(
                    history = history,
                    currentScheduleJson = currentJson,
                    imageBase64s = imageBase64s,
                )
                if (parsed == null) {
                    _errorMessage.value = str(R.string.msg_parse_failed)
                    _bubbles.value = _bubbles.value +
                            ChatBubble(fromUser = false, text = str(R.string.msg_parse_failed_reply))
                } else if (parsed.courses.isEmpty()) {
                    _errorMessage.value = str(R.string.msg_no_courses)
                    _bubbles.value = _bubbles.value +
                            ChatBubble(fromUser = false, text = str(R.string.msg_no_courses_reply))
                } else {
                    _schedule.value = parsed
                    // 持久化课表：后台划掉 App 再打开也不丢
                    ScheduleStore.save(parsed)
                    // 用 AI 给的学期起点（若有）+ 课程 endWeek 最大值，推断真实学期并存盘。
                    // 存上之后 ShiftclaDate 就不再依赖硬编码的 TERM_START / TERM_WEEKS，
                    // 换学期不用改代码。
                    TermInfoStore.save(
                        startDate = parsed.termStartDate,
                        weeks = parsed.courses.maxOfOrNull { it.endWeek } ?: 0,
                    )
                    // assistant 轮存原始 JSON，下一轮才能"接着改"
                    _transcript.value = history +
                            ChatTurn(role = "assistant", content = DeepSeekEngine.encodeSchedule(parsed))
                    _bubbles.value = _bubbles.value + ChatBubble(
                        fromUser = false,
                        text = summarize(parsed),
                    )
                }
            } catch (t: Throwable) {
                // parseSchedule 内部已经吞了异常，这里兜底防漏
                _errorMessage.value = t.message ?: str(R.string.msg_unknown_error)
            } finally {
                _isParsing.value = false
            }
        }
    }

    /** 手动清空错误提示（UI 弹完 toast 调一下）。 */
    fun consumeError() {
        _errorMessage.value = null
    }

    /** 清空对话（重开一轮导入）。课表保留。 */
    fun clearConversation() {
        _transcript.value = emptyList()
        _bubbles.value = emptyList()
    }

    /**
     * 清空**所有课程**：把课表、对话、AI 总结全部归零，并同步抹掉持久化。
     *
     * 触发入口：用户在导入框里说「清除/清空/删除所有课程」——[importSchedule]
     * 会先本地识别到该意图，走这里而不是发给 AI。
     */
    fun clearAllCourses() {
        _schedule.value = ScheduleResponse()
        ScheduleStore.clear()
        // 学期信息也一并清掉：课没了，学期推断就失去依据，留着会让空态误判阶段
        TermInfoStore.clear()
        _transcript.value = emptyList()
        _bubbles.value = emptyList()
        _errorMessage.value = null
    }

    /** AI 气泡的人话文案：说清"改了几门"，并把总评带上。 */
    private fun summarize(response: ScheduleResponse): String {
        val head = str(R.string.msg_updated_count, response.courses.size)
        return response.globalSummary?.let { "$head · $it" } ?: head
    }
}

/**
 * 本地判断用户这句话是不是「清除所有课程」的指令。
 *
 * 为什么做成**顶层纯函数**而不是 ViewModel 方法：它不依赖任何状态，抽出来才能
 * 在 JVM 单测里直接调（ViewModel 一构造就崩在 Main dispatcher，见记忆里的坑）。
 *
 * 为什么不做在线判断：发给 AI 既慢又要钱，而且当前 system prompt 是"解析引擎"，
 * 会把「清空」当成一段要找课程的文本。这里用关键词 + 否定词排除，够用且零依赖。
 */
internal fun isClearAllIntent(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty()) return false
    // 排除纯提问 / 否定表达，避免「怎么清空课程」「How do I clear all?」这类也误触发
    if (QUESTION_REGEX.containsMatchIn(t)) return false
    // 必须同时命中「动作」+「范围」+「整体」，中英文各一套关键词（用户说什么语言都认）
    val action = ACTION_REGEX.containsMatchIn(t)
    val scope = SCOPE_REGEX.containsMatchIn(t)
    // 纯「删掉 X 课」是单门删除，不是清空全部 —— 只有明确指向"所有/全部"才算
    val whole = WHOLE_REGEX.containsMatchIn(t)
    return action && scope && whole
}

// 中文关键词 + 英文关键词。用 IGNORE_CASE 让英文大小写不敏感。
private val QUESTION_REGEX = Regex("怎么|如何|为什么|？|\\?|吗$|^how\\b|^what\\b|^why\\b", RegexOption.IGNORE_CASE)
private val ACTION_REGEX = Regex("清|删|移除|重置|归零|不要|clear|delete|remove|reset|wipe|drop", RegexOption.IGNORE_CASE)

/** 动作作用的对象。可以是"整体"（所有/全部），也可以是一个容器（课表/课程/schedule）。 */
private val SCOPE_REGEX = Regex(
    "全部|所有|整个|整份|课表|课程|课|\\ball\\b|every|whole|entire|schedule|timetable|course|class",
    RegexOption.IGNORE_CASE,
)

/**
 * 必须表达"整体"，不能只是单门课。
 *
 * 注意这里**也把课表/schedule/timetable 算作整体** —— 「wipe my timetable」
 * 「删掉课表」语义上就是清空全部，不该因为没写 all/全部 就漏判。
 * 而单门课（delete Calculus / 删掉高数）既命中不了这里，也命中不了 SCOPE。
 */
private val WHOLE_REGEX = Regex(
    "全部|所有|整个|整份|清空|清零|不要了|课表" +
            "|\\ball\\b|every|everything|entire|whole|schedule|timetable",
    RegexOption.IGNORE_CASE,
)
