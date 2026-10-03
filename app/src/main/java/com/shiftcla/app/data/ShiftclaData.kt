@file:Suppress("SpellCheckingInspection")

package com.shiftcla.app.data

import android.content.Context
import android.content.SharedPreferences
import com.shiftcla.app.ui.dashboard.Course
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * AI 返回的**顶层包裹对象**。
 *
 * 之前直接要一个 `List<Course>`，但"课表"之外还需要一句全局总评来驱动
 * 手机端顶部提示语 / 平板端紫色卡片，所以升成对象。
 *
 * @param globalSummary 今日总评（限 20 字），null = AI 没给
 * @param courses       课程数组
 */
@Serializable
data class ScheduleResponse(
    val globalSummary: String? = null,
    /**
     * 注意这里给了默认空列表。
     * 模型偶尔会只回 `{"globalSummary": "..."}` 而漏掉 courses；
     * 没有默认值的话会抛 MissingFieldException，整个响应作废。
     */
    val courses: List<Course> = emptyList(),
    /**
     * 学期第 1 教学周周一的日期，ISO 格式 "2026-09-14"。
     *
     * 由 AI 从课表文本 / 截图里的校历信息（如「2026年秋季学期」「9月14日开学」）读出来；
     * 读不到时为 null。它驱动 [ShiftclaDate] 的「学期起点 / 当前第几周 / 学期阶段」判断，
     * 取代原先硬编码的 `TERM_START`。
     */
    val termStartDate: String? = null,
)

/**
 * 一轮对话。**只用于拼请求上下文**，UI 不要直接渲染它 ——
 * assistant 轮里存的是原始 JSON，直接显示会糊一屏乱码。
 *
 * @param role    "user" / "assistant"
 * @param content 用户原话，或模型返回的完整 JSON 文本
 */
data class ChatTurn(val role: String, val content: String)

/**
 * 展示给用户的气泡。和 [ChatTurn] 分开，是因为两者内容不一样：
 * 上下文要 JSON，界面要人话。
 *
 * @param fromUser true = 右侧用户气泡，false = 左侧 AI 气泡
 * @param text     人话文本
 */
data class ChatBubble(val fromUser: Boolean, val text: String)

/**
 * 全局 SharedPreferences 持有者。
 *
 * 从 [ApiKeyStore] 里抽出来，是为了让**同一份 prefs 文件**能被多个 Store 共享
 * （现在有两个：API Key、AI 写的季节性文案）。各自 `getSharedPreferences(文件名)`
 * 虽然底层也是同一实例，但文件名散在多处，改一次容易漏。
 *
 * 用法：`MainActivity.onCreate` 里 [init] 一次，其余地方 [get]。
 */
internal object ShiftclaPrefs {

    private const val FILE_NAME = "shiftcla_prefs"

    @Volatile
    private var prefs: SharedPreferences? = null

    /** 只调一次。传 applicationContext，避免持有 Activity。 */
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    /** 没 init 过返回 null，调用方按"读不到"处理，不崩。 */
    fun get(): SharedPreferences? = prefs
}

/**
 * API Key 的本地存储（SharedPreferences）。
 *
 * 用 SharedPreferences 而不是 DataStore 是权衡后的选择：这里只有一个字符串、
 * 读写都在主线程毫秒级完成，DataStore 的协程/Flow 包装属于净增复杂度。
 * 对外暴露的是 `StateFlow`，UI 侧用法跟 DataStore 一样。
 *
 * 使用前必须先 [init]（在 `Application` 或 Activity 的 `onCreate` 里调一次）。
 */
object ApiKeyStore {

    private const val KEY_API = "deepseek_api_key"

    private val _apiKey = MutableStateFlow("")
    /** 当前 Key；空串表示还没配置。 */
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    /** 是否已经从磁盘读过一次（避免重复覆盖内存里的值）。 */
    private var loaded = false

    /** 只调一次。传 applicationContext，避免持有 Activity。 */
    fun init(context: Context) {
        ShiftclaPrefs.init(context)
        if (loaded) return
        loaded = true
        _apiKey.value = ShiftclaPrefs.get()?.getString(KEY_API, "").orEmpty()
    }

    /** 读当前 Key（没 init 过会返回空串，不会崩）。 */
    fun current(): String = _apiKey.value

    /** 保存 Key。传空串等于清除。 */
    fun save(key: String) {
        val trimmed = key.trim()
        ShiftclaPrefs.get()?.edit()?.putString(KEY_API, trimmed)?.apply()
        _apiKey.value = trimmed
    }

    /** 是否已经配置过。 */
    val isConfigured: Boolean get() = _apiKey.value.isNotBlank()
}

/**
 * 学期信息（第 1 教学周周一 + 教学周总数）。
 *
 * @param startDate ISO "2026-09-14"
 * @param weeks     教学周数（含首尾），例如 17
 */
data class TermInfo(val startDate: String, val weeks: Int)

/**
 * 从课表数据推断出的学期信息的本地存储。
 *
 * 为什么需要它：`ShiftclaDate` 之前把学期起点 / 周数**硬编码**成
 * `TERM_START = 2026-09-14`、`TERM_WEEKS = 17`。换学期时两个常量都得手改，
 * 否则新学期开始后 App 仍会判定「学期已经过去了」。
 *
 * 现在改成：**用户导入课表时，从 AI 返回的 `termStartDate` + 课程 `endWeek` 最大值
 * 推断出真实学期，存进 SharedPreferences**。`ShiftclaDate` 优先读这里，读不到才
 * 回退到硬编码（此时硬编码只是「没导入过课表」的兜底，不再是单一事实源）。
 */
object TermInfoStore {

    private const val KEY_START = "term_start"
    private const val KEY_WEEKS = "term_weeks"

    /**
     * 读当前学期信息。没存过 / 存了但非法（日期解析不了、周数 ≤0）都返回 null，
     * 调用方回退到硬编码兜底。
     */
    fun load(): TermInfo? {
        val prefs = ShiftclaPrefs.get() ?: return null
        val start = prefs.getString(KEY_START, null) ?: return null
        val weeks = prefs.getInt(KEY_WEEKS, 0)
        if (weeks <= 0) return null
        if (ShiftclaDate.parseIsoDate(start) == null) return null
        return TermInfo(start, weeks)
    }

    /**
     * 保存推断出的学期信息。非法输入（起点解析不了、周数 ≤0）静默忽略，
     * 绝不写脏数据去污染后续判断。
     */
    fun save(startDate: String?, weeks: Int) {
        if (startDate.isNullOrBlank() || weeks <= 0) return
        if (ShiftclaDate.parseIsoDate(startDate) == null) return
        val prefs = ShiftclaPrefs.get() ?: return
        prefs.edit()
            .putString(KEY_START, startDate)
            .putInt(KEY_WEEKS, weeks)
            .apply()
    }

    /** 清空推断出的学期信息（清空课表时一起调用，避免空态误判学期阶段）。 */
    fun clear() {
        ShiftclaPrefs.get()?.edit()
            ?.remove(KEY_START)
            ?.remove(KEY_WEEKS)
            ?.apply()
    }
}

/**
 * 课表（[ScheduleResponse]）的本地持久化。
 *
 * 背景：之前 `_schedule` 只存在 ViewModel 内存里，**后台划掉 App 再打开就全没了**。
 * 这里在「AI 解析成功」后把整份课表序列化成 JSON 存进 SharedPreferences，
 * ViewModel 构造时读回来，实现冷启动恢复。
 *
 * 序列化复用 [DeepSeekEngine.encodeSchedule] / [DeepSeekEngine.decodeSchedule]，
 * 不另起一套 Json 配置（避免两份序列化逻辑漂移）。
 */
object ScheduleStore {

    private const val KEY_SCHEDULE = "schedule_json"

    /** 读回持久化的课表。没存过 / JSON 损坏都返回 null，调用方当"空课表"处理。 */
    fun load(): ScheduleResponse? {
        val json = ShiftclaPrefs.get()?.getString(KEY_SCHEDULE, null) ?: return null
        if (json.isBlank()) return null
        return runCatching { DeepSeekEngine.decodeSchedule(json) }.getOrNull()
    }

    /** 保存课表。空课表（courses 为空）也照存 —— 用户明确清空后不该又"复活"旧数据。 */
    fun save(schedule: ScheduleResponse) {
        val prefs = ShiftclaPrefs.get() ?: return
        prefs.edit().putString(KEY_SCHEDULE, DeepSeekEngine.encodeSchedule(schedule)).apply()
    }

    /** 清空（用户主动"重开一轮"时可选）。 */
    fun clear() {
        ShiftclaPrefs.get()?.edit()?.remove(KEY_SCHEDULE)?.apply()
    }
}
