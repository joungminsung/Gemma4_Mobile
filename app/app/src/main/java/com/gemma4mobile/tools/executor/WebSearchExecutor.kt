package com.gemma4mobile.tools.executor

import android.util.Log
import com.gemma4mobile.tools.ToolExecutor
import com.gemma4mobile.tools.ToolName
import com.gemma4mobile.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSearchExecutor @Inject constructor(
    private val httpClient: OkHttpClient,
) : ToolExecutor {

    override val toolName = ToolName.SEARCH_WEB

    override suspend fun execute(arguments: Map<String, Any>): ToolResult {
        val query = arguments["query"]?.toString()
            ?: return ToolResult(name = toolName.displayName, error = "query is required")

        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "https://html.duckduckgo.com/html/?q=$encoded"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .build()

                val response = httpClient.newCall(request).execute()
                val html = response.body?.string() ?: ""

                val items = parseSearchResults(html)
                val result = JSONObject()
                result.put("query", query)
                result.put("items", items)
                result.put("count", items.length())

                ToolResult(name = toolName.displayName, result = result)
            } catch (e: Exception) {
                Log.e(TAG, "Search failed: ${e.message}", e)
                ToolResult(name = toolName.displayName, error = "검색 실패: ${e.message}")
            }
        }
    }

    private fun parseSearchResults(html: String): JSONArray {
        val items = JSONArray()
        val resultPattern = Regex(
            """<div[^>]*class="[^"]*result[^"]*"[^>]*>.*?</div>\s*</div>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val titlePattern = Regex("""<a[^>]*class="result__a"[^>]*href="([^"]*)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snippetPattern = Regex("""<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

        var count = 0
        for (match in resultPattern.findAll(html)) {
            if (count >= 5) break
            val block = match.value

            val titleMatch = titlePattern.find(block) ?: continue
            val snippetMatch = snippetPattern.find(block)

            val rawUrl = titleMatch.groupValues[1]
            val url = decodeRedirectUrl(rawUrl)
            val title = stripHtml(titleMatch.groupValues[2])
            val snippet = snippetMatch?.let { stripHtml(it.groupValues[1]) } ?: ""

            if (title.isNotBlank() && url.isNotBlank()) {
                val item = JSONObject()
                item.put("title", title)
                item.put("snippet", snippet)
                item.put("url", url)
                items.put(item)
                count++
            }
        }
        return items
    }

    private fun decodeRedirectUrl(url: String): String {
        if (url.contains("uddg=")) {
            val encoded = url.substringAfter("uddg=").substringBefore("&")
            return java.net.URLDecoder.decode(encoded, "UTF-8")
        }
        return url
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .trim()
    }

    companion object {
        private const val TAG = "WebSearchExecutor"
    }
}
