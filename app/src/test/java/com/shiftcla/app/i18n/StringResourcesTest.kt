package com.shiftcla.app.i18n

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 国际化资源的一致性检查。
 *
 * 国际化最典型的 bug 不是"翻错"，而是**漏翻**——新增一句文案只往默认语言里加，
 * 另一种语言悄悄少一条，界面就露出默认语言。这类问题编译器不管、肉眼也难发现，
 * 所以直接读 XML 做集合比对。
 *
 * 这里读的是源文件而不是编译产物：单测的 CWD 是模块目录（app/），
 * 往上找到 `src/main/res` 即可，不需要 Robolectric 起 Android 环境。
 */
class StringResourcesTest {

    private fun resDir(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "src/main/res")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        fail("找不到 src/main/res，CWD=${File("").absolutePath}")
    }

    /** 抽出 `name="xxx"` 的集合。用正则足够——这里只是比对 key，不做完整 XML 语义。 */
    private fun stringNames(file: File): Set<String> {
        assertTrue(file.isFile, "资源文件不存在：${file.path}")
        val text = file.readText()
        return Regex("""<string\s+name="([^"]+)"""").findAll(text).map { it.groupValues[1] }.toSet()
    }

    private fun stringValue(file: File, name: String): String? {
        val text = file.readText()
        val m = Regex("""<string\s+name="$name"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(text) ?: return null
        return m.groupValues[1]
    }

    private val defaultStrings by lazy { File(resDir(), "values/strings.xml") }
    private val zhStrings by lazy { File(resDir(), "values-zh/strings.xml") }

    @Test
    fun `中英文资源的 key 必须一一对应`() {
        val en = stringNames(defaultStrings)
        val zh = stringNames(zhStrings)

        val missingInZh = en - zh
        val missingInEn = zh - en

        assertTrue(
            missingInZh.isEmpty(),
            "中文缺这些条目（会退回默认语言显示）：$missingInZh",
        )
        assertTrue(
            missingInEn.isEmpty(),
            "默认（英文）缺这些条目：$missingInEn",
        )
        assertEquals(en, zh, "两侧 key 集合不一致")
    }

    @Test
    fun `学期结束的兜底文案两种语言都要 放松 + 提醒导入 两层意思`() {
        // 这是用户明确提过的需求，文案换了但意思不能丢。
        // 中文：松一口气 + 提醒导新课表；英文：take a break + import next term's schedule
        val zh = stringValue(zhStrings, "empty_after_term_fallback")
        assertTrue(zh != null && zh.contains("松"), "中文兜底少了【放松】：$zh")
        assertTrue(zh != null && zh.contains("课表"), "中文兜底少了【提醒导入】：$zh")

        val en = stringValue(defaultStrings, "empty_after_term_fallback")
        val lower = en.orEmpty().lowercase()
        assertTrue(
            lower.contains("break") || lower.contains("relax"),
            "英文兜底少了【放松】：$en",
        )
        assertTrue(lower.contains("schedule"), "英文兜底少了【提醒导入】：$en")
    }

    @Test
    fun `带参数的文案两侧占位符要一致`() {
        // 占位符对不上会在运行时抛 IllegalFormatException —— 属于 Crash 级问题
        for (name in listOf("msg_updated_count", "detail_ai_note")) {
            val en = stringValue(defaultStrings, name) ?: fail("英文缺 $name")
            val zh = stringValue(zhStrings, name) ?: fail("中文缺 $name")
            val pattern = Regex("""%\d+\$[sd]""")
            assertEquals(
                pattern.findAll(en).map { it.value }.toSortedSet(),
                pattern.findAll(zh).map { it.value }.toSortedSet(),
                "$name 两侧占位符不一致：en=[$en] zh=[$zh]",
            )
        }
    }
}
