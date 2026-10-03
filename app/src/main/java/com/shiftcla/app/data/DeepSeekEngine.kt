@file:Suppress("SpellCheckingInspection")

package com.shiftcla.app.data

import com.shiftcla.app.ui.dashboard.Course
import com.shiftcla.app.ui.dashboard.CourseBlock
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json

/* ============================================================================
 *  DeepSeekEngine · 把教务系统的原始文本丢给 DeepSeek，拿回结构化课程
 *  ---------------------------------------------------------------------------
 *  职责边界：这一层只做「网络 + 提示词 + 容错解析」。
 *  它不认识 Compose，也不持有任何 UI 状态 —— 解析出来就是现成的 List<Course>，
 *  可以直接喂给看板。
 *
 *  关于「容错」：大模型返回 JSON 时经常手抖，实测至少三种花样
 *    1. 规规矩矩一个数组
 *    2. 包在 ```json ... ``` 里
 *    3. 前面先来一句"好的，以下是解析结果："再给数组
 *  所以拿到 content 后必须**先去壳再取方括号区间**，不能直接 decodeFromString。
 * ========================================================================== */

/**
 * 发送给 Chat API 的一条消息。
 *
 * `content` 用 [JsonElement] 而不是 String，是为了同时兼容两种 payload：
 *  · 纯文本：`"content": "..."`            → [JsonPrimitive]
 *  · 带图：  `"content": [{type:"text"..},{type:"image_url"..}]` → [JsonArray]
 * 用 sealed class + 自定义序列化器也能做，但要写几十行；这里直接拼 JSON 更省事。
 */
@Serializable
internal data class ChatMessage(val role: String, val content: JsonElement)

/** 请求体。`stream` 固定 false —— 我们要的是一次性拿完整 JSON。 */
@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
)

/** 响应里我们只关心 choices[].message.content，其余字段靠 ignoreUnknownKeys 忽略。 */
@Serializable
private data class ChatResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
private data class ChatChoice(
    @SerialName("message") val message: ResponseMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

/** 响应里的 message 固定是纯文本 content，跟请求侧的联合类型分开定义更省心。 */
@Serializable
private data class ResponseMessage(val role: String = "assistant", val content: String = "")

/**
 * DeepSeek Chat API 客户端（单例）。
 *
 * 用法：
 * ```
 * val courses = DeepSeekEngine.parseSchedule(rawText = 从教务复制的一坨文本, apiKey = "sk-...")
 * ```
 */
object DeepSeekEngine {

    /** DeepSeek 标准 Chat Completions 端点。要换自建网关改这里就行。 */
    const val ENDPOINT: String = "https://api.deepseek.com/chat/completions"

    /** 固定用 deepseek-chat；换 deepseek-reasoner 的话要把思考过程也剥掉。 */
    const val MODEL: String = "deepseek-chat"

    /**
     * 系统提示词。要点：
     *  · 明确"只返回 JSON 数组"、明确禁止 Markdown 代码块 —— 双保险；
     *  · 把字段名和 dayOfWeek 的取值范围写死，避免模型自由发挥键名；
     *  · **字段清单必须包含周次三件套**，否则模型会漏填、整门课退化成"每周都上"。
     */
    /** 提示词的固定部分。日期上下文由 [buildSystemPrompt] 拼在前面。 */
    const val SYSTEM_PROMPT: String =
        "你是一个无情的教务数据解析引擎。仅返回一个合法的 JSON 对象，绝不要包含 Markdown 标记（如 ```json）或任何其他文字。\n" +
                "【顶层结构】{ globalSummary: string, termStartDate: string|null, courses: Course[] }。\n" +
                "【termStartDate】学期第 1 教学周**周一**的日期，格式 YYYY-MM-DD（如 2026-09-14）。\n" +
                "    · 从课表文本 / 截图里的校历信息读（如「2026年秋季学期」「9月14日开学」「第1周 9.14-9.20」）；\n" +
                "    · **只认能明确对应到具体日期的**；读不到 / 课表里没写就填 null，**绝不要编造或猜一个日期**；\n" +
                "    · 注意「预备周 / 第0周」不算第 1 教学周，别把预备周周一的日期填进去。\n" +
                "【globalSummary】用极客或学长口吻，基于整体课表给出一句今日总评或建议，**限 20 字以内**；" +
                "课表很满/有考试要点出来，没什么特别的就轻松一句。\n" +
                // ⚠️ 这一行曾经只列了 8 个字段，**漏掉了 startWeek / endWeek / weekParity**。
                // 模型很听话地只填这 8 个 → 周次落回默认值 1..20 → 相当于"每周都上" →
                // 表现就是用户报的「别的周的课老被加到本周来」。字段清单必须跟着周次规则一起列。
                "【courses】每个元素必须包含 11 个键：courseName, teacher, location, " +
                "dayOfWeek (1-7), startTime, endTime, startWeek, endWeek, weekParity, tag, aiNote。\n" +
                "【硬性约定】\n" +
                "1. dayOfWeek 用 1-7 表示周一至周日，必须依据课表里出现的星期字样填写，不要一律写 1；\n" +
                "2. startTime / endTime 一律 24 小时制 HH:mm，补零（如 08:00、14:05），不要写「下午两点」；\n" +
                "3. **返回输入里能找到的全部课程**，不要只挑今天的 —— App 会自行按当天和当周筛选；\n" +
                "3.1 教务课表通常覆盖整个学期（如「1-16周」「单周」「双周」），**每门课都必须填周次：**\n" +
                "    · startWeek / endWeek 填教学周起止（含两端），例如「1-16周」→ startWeek=1, endWeek=16；\n" +
                "    · 单周填 weekParity=\"odd\"，双周填 \"even\"，每周都有填 null；\n" +
                "    · 输入没写周次时，startWeek=1、endWeek=20、weekParity=null（当作全学期）；\n" +
                "    · **同一门课跨多周只输出一条**，用 startWeek~endWeek 表示范围，" +
                "绝对不要每周输出一条，也不要把别的周的课复制到本周；\n" +
                "    · 不要自己按周次或星期筛选，全部输出，App 会筛。\n" +
                "4. tag 只在确有「测验/考试/线上/重点/上机」等标记时填，否则给 null；\n" +
                "5. aiNote 写一句对这门课有用的提醒（如「今天有随堂测验」），没有就给 null；\n" +
                "6. location / teacher 缺失时给空字符串，不要编造。\n" +
                "【空输入】用户没给有效课表时，courses 返回空数组，globalSummary 说明没看懂输入。"

    /**
     * 多轮对话时的补充规则：告诉模型它不只是"解析一次"，而是**可以改课表**。
     * 拼在 [SYSTEM_PROMPT] 之后，只在非首轮出现。
     */
    const val ADJUST_PROMPT: String =
        "\n【多轮修改】这是一次连续对话。用户可能要求你**修改**已有课表（换教室、改时间、删掉某门课、" +
                "加一门课、调整顺序等）。此时你会收到一段「当前课表」上下文，请**在它的基础上做增量修改**，" +
                "并始终返回**修改后的完整课表**（不是只返回改动的那几条）。\n" +
                "用户只是提问（比如「周三有什么课」）时，不要改动课表，原样返回当前课表，" +
                "并把回答写进 globalSummary。"

    /** 「当前课表」上下文的前缀，后接课表 JSON。 */
    private const val CURRENT_SCHEDULE_PREFIX = "当前课表（JSON）："

    /**
     * 写「季节性文案」用的系统提示词 —— 目前只有学期结束后那一句。
     *
     * 和 [SYSTEM_PROMPT] 的关键区别：这里要的是**一句人话**，不是 JSON。
     * 所以必须显式禁止 Markdown 和换行，并且**不复用** [buildSystemPrompt]
     * （那套提示词会把模型推向"输出 JSON 对象"）。
     */
    const val TERM_NOTE_PROMPT: String =
        "你是课表 App「Shiftcla」里的学长，说话简短、口语、有温度，不说教也不客套。" +
                "只输出一句话：不要引号、不要 Markdown、不要换行、不要分段、不要解释你在做什么。"

    /**
     * 拼出最终的系统提示词：在固定规则前加一句当前日期。
     *
     * 没有这句的话，用户写「明天下午的课」「这周五」这类相对日期，模型只能瞎猜；
     * 有了它才能落到正确的 dayOfWeek 上。
     */
    internal fun buildSystemPrompt(todayContext: String? = null): String =
        if (todayContext.isNullOrBlank()) SYSTEM_PROMPT else "$todayContext\n$SYSTEM_PROMPT"

    /** 客户端懒加载：只有真正发请求时才建连接池。 */
    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(EngineJson)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000   // 大模型首字有时要等十几秒
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
            }
        }
    }

    /**
     * 把教务原始文本解析成课程列表。
     *
     * @param rawText 从教务系统复制出来的任意文本（课表粘贴、HTML 片段、截图 OCR 都行）
     * @param apiKey  DeepSeek API Key，会以 `Authorization: Bearer <key>` 注入
     * @return 解析成功返回课程列表；网络失败 / 返回体不合法 / JSON 解析失败都返回 null
     *         （调用方只需判空，不必再区分异常类型）
     */
    suspend fun parseSchedule(
        rawText: String,
        apiKey: String = "",
        imageBase64s: List<String> = emptyList(),
        model: String = MODEL,
    ): ScheduleResponse? = chat(
        history = listOf(ChatTurn(role = "user", content = rawText)),
        currentScheduleJson = null,
        apiKey = apiKey,
        imageBase64s = imageBase64s,
        model = model,
    )

    /**
     * **一轮对话**：既是首次解析，也是后续的"改课表"。
     *
     * 与一次性 [parseSchedule] 的区别在于两点：
     *  1. 带上完整 [history]，模型能看到前几轮说过什么；
     *  2. 带上 [currentScheduleJson] —— 这是"能改"的关键：不把当前课表喂回去，
     *     模型每次只会从零重新解析用户的这句话，改不了已有的东西。
     *
     * @param history            对话历史（user / assistant 交替），assistant 轮存的是当时的完整 JSON
     * @param currentScheduleJson 当前生效的课表 JSON；首轮为 null
     * @return 修改后的完整课表；失败返回 null
     */
    suspend fun chat(
        history: List<ChatTurn>,
        currentScheduleJson: String? = null,
        apiKey: String = "",
        imageBase64s: List<String> = emptyList(),
        model: String = MODEL,
    ): ScheduleResponse? {
        // Key 优先用参数；参数为空时回落到本地存储里用户配置的那个
        val key = apiKey.ifBlank { ApiKeyStore.current() }
        if (key.isBlank()) return null

        val images = imageBase64s.filter { it.isNotBlank() }
        if (history.isEmpty() || (history.last().content.isBlank() && images.isEmpty())) return null

        return try {
            val response: HttpResponse = client.post(ENDPOINT) {
                header(HttpHeaders.Authorization, "Bearer $key")
                contentType(ContentType.Application.Json)
                setBody(
                    ChatRequest(
                        model = model,
                        messages = buildMessages(history, currentScheduleJson, images),
                    )
                )
            }
            if (!response.status.isSuccess()) return null

            val content = response.body<ChatResponse>()
                .choices.firstOrNull()
                ?.message
                ?.content
                ?.takeIf { it.isNotBlank() }
                ?: return null

            parseContent(content)
        } catch (t: Throwable) {
            // 网络异常、超时、序列化异常统一吞掉返回 null —— 上层只关心"成没成"
            null
        }
    }

    /**
     * 让 AI 写一句**纯文本**（不解析 JSON）。
     *
     * 用途：学期结束后界面上那句"先放松一下 + 记得导入新学期课表"。
     * 跟 [chat] 共用同一个 client 和超时配置，但走独立的 system prompt，
     * 因为这里要的是人话而不是结构化数据。
     *
     * @param userPrompt 具体要它写什么（由调用方拼好日期上下文）
     * @return 清洗后的单行文本；未配 Key / 网络失败 / 返回空 都返回 null
     */
    suspend fun writeTermNote(userPrompt: String, apiKey: String = "", model: String = MODEL): String? {
        val key = apiKey.ifBlank { ApiKeyStore.current() }
        if (key.isBlank() || userPrompt.isBlank()) return null

        return try {
            val response: HttpResponse = client.post(ENDPOINT) {
                header(HttpHeaders.Authorization, "Bearer $key")
                contentType(ContentType.Application.Json)
                setBody(
                    ChatRequest(
                        model = model,
                        messages = listOf(
                            ChatMessage("system", JsonPrimitive(TERM_NOTE_PROMPT)),
                            ChatMessage("user", JsonPrimitive(userPrompt)),
                        ),
                    )
                )
            }
            if (!response.status.isSuccess()) return null

            response.body<ChatResponse>()
                .choices.firstOrNull()
                ?.message
                ?.content
                ?.let(::sanitizeNote)
                ?.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * 把模型写的这句话收拾干净 —— 它总爱加引号、加换行、给你来段 Markdown。
     *
     * 抽成 internal 纯函数是为了能单测（网络那半没法测，清洗这半全是分支）。
     */
    internal fun sanitizeNote(raw: String): String =
        raw.trim()
            .trim('"', '\'', '“', '”', '「', '」')
            // 去掉 Markdown 强调符号，保留正文
            .replace("**", "")
            .replace("*", "")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()

    /**
     * 把对话历史拼成 API 的 messages 数组。
     *
     * 顺序很关键：
     *  1. system（日期上下文 + 固定规则 [+ 多轮修改规则]）
     *  2. system（当前课表）——有课表时才加，且**放在历史之前**，
     *     让模型先建立起"现在长什么样"的基准，再读用户怎么说
     *  3. 历史逐轮追加，**图片只挂在最后一条 user 上**
     */
    internal fun buildMessages(
        history: List<ChatTurn>,
        currentScheduleJson: String? = null,
        images: List<String> = emptyList(),
    ): List<ChatMessage> = buildList {
        val base = buildSystemPrompt(ShiftclaDate.snapshot().promptContext)
        val isFollowUp = !currentScheduleJson.isNullOrBlank()
        add(ChatMessage("system", JsonPrimitive(if (isFollowUp) base + ADJUST_PROMPT else base)))

        if (isFollowUp) {
            add(ChatMessage("system", JsonPrimitive(CURRENT_SCHEDULE_PREFIX + currentScheduleJson)))
        }

        history.forEachIndexed { index, turn ->
            val attachImages = index == history.lastIndex && turn.role == "user"
            add(
                ChatMessage(
                    role = turn.role,
                    content = if (attachImages) buildUserContent(turn.content, images)
                    else JsonPrimitive(turn.content),
                )
            )
        }
    }

    /** 把课表序列化，供下一轮作为"当前课表"喂回去。 */
    fun encodeSchedule(schedule: ScheduleResponse): String =
        EngineJson.encodeToString(schedule)

    /**
     * 组装 user 消息的 content。
     *
     * 没有图片 → 直接一个字符串；有图片 → 按 OpenAI Vision 兼容格式数组化。
     * 顺序：**文本在前、图片依次在后**（模型先看到任务描述，再看图）。
     * 多张图就是多个 `image_url` 段，各家兼容实现都支持。
     */
    internal fun buildUserContent(rawText: String, imageBase64s: List<String>): JsonElement {
        val images = imageBase64s.filter { it.isNotBlank() }
        if (images.isEmpty()) return JsonPrimitive(rawText)

        return buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", rawText)
                }
            )
            images.forEach { base64 ->
                add(
                    buildJsonObject {
                        put("type", "image_url")
                        put(
                            "image_url",
                            buildJsonObject {
                                // data URL 格式，兼容 OpenAI / 各家兼容实现
                                put("url", "data:image/jpeg;base64,$base64")
                            },
                        )
                    }
                )
            }
        }
    }

    /**
     * 从模型返回的 `content` 字符串里解析出课程列表。
     *
     * 拆成独立函数是为了**可以脱离网络做单元测试**（见 `app/src/test`）。
     */
    internal fun parseContent(content: String): ScheduleResponse? =
        try {
            decodeSchedule(extractJsonPayload(content))
        } catch (t: Throwable) {
            null
        }

    /**
     * 剥掉模型可能带上的"壳"，只留下 JSON 数组本体。
     *
     * 三道防线：去空白 → 正则删 ``` / ```json 围栏 → 截取第一个 `[` 到最后一个 `]`。
     * 最后那一步同时干掉了"好的，以下是结果："这类前导废话。
     */
    internal fun extractJsonPayload(raw: String): String {
        var text = raw.trim().removePrefix("\uFEFF")

        // 去掉 Markdown 代码围栏：```json / ```JSON / ``` 都吃掉
        text = FENCE_REGEX.replace(text, "").trim()

        // 两种合法开头：`{...}` 包裹对象、或 `[...]` 裸数组。
        // 判据必须是"谁先出现"，不能简单优先 `{` ——
        // 裸数组 `[{"a":1}]` 里第一个 `{` 在 `[` 之后，优先 `{` 会把数组切成对象。
        val arrStart = text.indexOf('[')
        val objStart = text.indexOf('{')
        val start = when {
            arrStart < 0 -> objStart
            objStart < 0 -> arrStart
            else -> minOf(arrStart, objStart)
        }
        if (start < 0) return text

        val end = maxOf(text.lastIndexOf(']'), text.lastIndexOf('}'))
        return if (end > start) text.substring(start, end + 1) else text
    }

    /**
     * 反序列化 + 补全 AI 不负责的派生字段。
     *
     * 补两样东西：
     *  · `id`：LazyGrid 的 key，必须唯一且稳定 —— AI 不给，按序号生成；
     *  · `block`：看板 Expanded 布局要按上午/下午分半区 —— 由 startTime 推出来。
     */
    internal fun decodeSchedule(jsonText: String): ScheduleResponse {
        // 两种输入都接：包裹对象、或模型偷懒只给的裸数组
        val parsed: ScheduleResponse = if (jsonText.trimStart().startsWith("[")) {
            ScheduleResponse(courses = EngineJson.decodeFromString<List<Course>>(jsonText))
        } else {
            EngineJson.decodeFromString<ScheduleResponse>(jsonText)
        }

        return parsed.copy(
            globalSummary = parsed.globalSummary?.trim()?.takeIf { it.isNotEmpty() },
            // termStartDate 也顺手清洗：AI 可能给 " 2026-09-14 " 或空串
            termStartDate = parsed.termStartDate?.trim()?.takeIf { it.isNotEmpty() },
            courses = parsed.courses.mapIndexed { index, course ->
                course.copy(
                    id = course.id.ifBlank { "ds-$index" },
                    // block 缺省时由 startTime 推；显式给了 Afternoon 就尊重它
                    block = course.block.takeIf { it != CourseBlock.Morning }
                        ?: inferBlock(course.startTime),
                )
            },
        )
    }

    /** 按开始时间判断上午/下午：12:00 之前算上午，解析不出来时保守按上午。 */
    internal fun inferBlock(startTime: String): CourseBlock {
        val hour = startTime.substringBefore(':').trim().toIntOrNull() ?: return CourseBlock.Morning
        return if (hour >= 12) CourseBlock.Afternoon else CourseBlock.Morning
    }

    /** 整个引擎共用的 Json：忽略未知键 + 不用双引号也能解 + 缺字段走默认值。 */
    private val EngineJson: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true   // 字段类型对不上（如 null 给了非空 String）时退回默认值
    }

    private val FENCE_REGEX = Regex("```[a-zA-Z]*[ \\t]*\\r?\\n?|```")
}
