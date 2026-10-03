@file:Suppress("SpellCheckingInspection")

package com.shiftcla.app.data

/**
 * 学期结束后那句「季节性文案」。
 *
 * 需求来自用户：*"假期就让 ai 写一点让用户去放松的话"* +
 * *"学期过就显示学期已经过去了之类的话，让 ai 提示需要用户提供新的教务数据"*。
 * 所以这句文案要同时说两件事：**先让人放松，再提醒导入新学期课表**。
 *
 * 为什么要在本地缓存：这句话一学期只需要一句。不做缓存的话，
 * 用户每次冷启动 App 都要发一次 API 请求 —— 慢、费 token，还没网就退化成兜底文案。
 * 缓存键带上 [ShiftclaDate.Snapshot.termKey]（学期起点日期），换学期自动失效重写。
 */
object TermNote {

    /**
     * 兜底文案：**没配 API Key，或者请求失败**时用。
     *
     * 措辞刻意和 AI 那版保持同一口径（放松 + 提醒），这样降级后界面不会"变了个风格"。
     */
    const val FALLBACK: String =
        "学期结束啦，先好好松口气 ☕ 新学期课表出来后，点左上角菜单导进来就行"

    private const val KEY_PREFIX = "term_note_"

    /** 读缓存的文案；没有（或 termKey 为空）返回 null。 */
    fun cached(termKey: String): String? {
        if (termKey.isBlank()) return null
        return ShiftclaPrefs.get()
            ?.getString(KEY_PREFIX + termKey, null)
            ?.takeIf { it.isNotBlank() }
    }

    /** 写入缓存。termKey / note 为空时静默忽略（不写脏数据）。 */
    fun save(termKey: String, note: String) {
        if (termKey.isBlank() || note.isBlank()) return
        ShiftclaPrefs.get()?.edit()?.putString(KEY_PREFIX + termKey, note)?.apply()
    }

    /**
     * 拼出给 AI 的指令。
     *
     * 抽成纯函数是为了能单测 —— 这里全是字符串拼接，段位很容易拼歪，
     * 而"拼歪了"在真机上表现为"AI 答得莫名其妙"，很难定位。
     *
     * @param snapshot 当前日期快照（[ShiftclaDate.Snapshot.phase] 应为 AfterTerm）
     */
    fun prompt(snapshot: ShiftclaDate.Snapshot): String = buildString {
        // promptContext 形如「今天是 2026年1月20日，周二（dayOfWeek=2）…」，
        // 取到括号前就够 AI 定位日期了，不用把 dayOfWeek 这种内部编号丢给它。
        append("今天是 ").append(snapshot.termKeyDateLabel()).append("。")
        append("我这个学期（第 1-").append(ShiftclaDate.termWeeks()).append(" 教学周）")
        snapshot.termEndLabel?.let { append("已经在 $it 结束") } ?: append("已经结束")
        append("了。")
        append("请写一句话（40 字以内）给我：")
        append("先说一句让我放松下来的话，再顺带提醒我导入下个学期的课表。")
    }

    /** "今天是 2026年10月1日，周四（dayOfWeek=4）…" → "2026年10月1日，周四" */
    private fun ShiftclaDate.Snapshot.termKeyDateLabel(): String =
        promptContext.removePrefix("今天是 ").substringBefore("（").trim()
}
