package app.yougram.plugin

import org.json.JSONObject

sealed interface PluginWidget { val id: String }

data class PText(override val id: String, val text: String) : PluginWidget

data class PSection(override val id: String, val title: String) : PluginWidget

data class PSwitch(override val id: String, val label: String, val value: Boolean) : PluginWidget

data class PInput(
    override val id: String,
    val label: String,
    val value: String,
    val hint: String,
) : PluginWidget

data class PButton(override val id: String, val label: String) : PluginWidget

data class PluginPage(
    val pluginId: String,
    val id: String,
    val title: String,
    val items: List<PluginWidget>,
)

/**
 * Разбор JSON-описания страницы настроек плагина (host->call("ui_set_page", ...)).
 *
 * Лимиты подобраны с запасом: страница — это небольшой набор виджетов,
 * нативные плагины не должны через UI выкачивать память или рисовать «стену».
 */
object PluginUiParser {
    private const val MAX_BYTES = 64 * 1024
    private const val MAX_ITEMS = 100
    private const val MAX_TEXT = 2000
    private val ID = Regex("[A-Za-z0-9._-]{1,64}")

    fun parse(pluginId: String, json: String): PluginPage {
        require(json.length <= MAX_BYTES) { "Страница слишком большая" }

        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: throw IllegalArgumentException("Некорректный JSON")

        val pageId = root.optString("id", "main")
        require(ID.matches(pageId)) { "Недопустимый id страницы" }

        val arr = root.optJSONArray("items")
            ?: throw IllegalArgumentException("Нет поля items")
        require(arr.length() <= MAX_ITEMS) { "Слишком много элементов (максимум $MAX_ITEMS)" }

        val seen = HashSet<String>()
        val items = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i)
                    ?: throw IllegalArgumentException("Элемент $i не объект")

                val type = o.optString("type")
                val id = o.optString("id", "item$i")
                require(ID.matches(id)) { "Недопустимый id элемента: $id" }
                require(seen.add(id)) { "Повторяющийся id элемента: $id" }

                fun s(key: String) = o.optString(key, "").take(MAX_TEXT)

                add(
                    when (type) {
                        "text" -> PText(id, s("text"))
                        "section" -> PSection(id, s("title"))
                        "switch" -> PSwitch(id, s("label"), o.optBoolean("value", false))
                        "input" -> PInput(id, s("label"), s("value"), s("hint"))
                        "button" -> PButton(id, s("label"))
                        else -> throw IllegalArgumentException("Неизвестный тип элемента: $type")
                    },
                )
            }
        }

        return PluginPage(
            pluginId = pluginId,
            id = pageId,
            title = root.optString("title", "").take(120),
            items = items,
        )
    }
}
