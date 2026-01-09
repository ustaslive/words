package com.ustas.words

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val DICTIONARY_ASSET_NAME = "words.txt"
private const val DICTIONARY_OVERRIDE_FILE_NAME = "words_override.txt"
private const val DICTIONARY_TEMP_FILE_NAME = "words_override.tmp"
private const val DICTIONARY_PREFS_NAME = "words_settings"
private const val KEY_DICTIONARY_ETAG = "dictionary_etag"
private const val KEY_DICTIONARY_LAST_MODIFIED = "dictionary_last_modified"
private const val KEY_DICTIONARY_LAST_CHECK = "dictionary_last_check"
private const val KEY_DICTIONARY_LAST_SUCCESS = "dictionary_last_success"
private const val DEFAULT_LAST_CHECK_MS = 0L
private const val MILLISECONDS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val DICTIONARY_UPDATE_INTERVAL_MS =
    HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE * MILLISECONDS_PER_SECOND
private const val DICTIONARY_CONNECT_TIMEOUT_MS = 10_000
private const val DICTIONARY_READ_TIMEOUT_MS = 15_000
private const val DICTIONARY_LINE_SEPARATOR = "\n"
private const val HEADER_ETAG = "ETag"
private const val HEADER_LAST_MODIFIED = "Last-Modified"
private const val HEADER_IF_NONE_MATCH = "If-None-Match"
private const val HEADER_IF_MODIFIED_SINCE = "If-Modified-Since"
private const val HEADER_USER_AGENT = "User-Agent"
private const val USER_AGENT_VALUE = "WordsApp"
// Replace with your repo raw URL.
private const val DICTIONARY_URL = "https://raw.githubusercontent.com/ustaslive/words/develop/app/src/main/assets/words.txt"

internal enum class DictionaryUpdateReason {
    Scheduled,
    Manual
}

internal sealed interface DictionaryUpdateResult {
    data class Updated(val words: List<String>) : DictionaryUpdateResult
    data object NotModified : DictionaryUpdateResult
    data object Skipped : DictionaryUpdateResult
    data class Failed(val errorMessage: String) : DictionaryUpdateResult
}

internal fun loadDictionaryWords(context: Context): List<String> {
    val overrideFile = dictionaryOverrideFile(context)
    val overrideWords = readDictionaryFile(overrideFile)
    if (overrideWords.isNotEmpty()) {
        return overrideWords
    }
    return loadWordList { context.assets.open(DICTIONARY_ASSET_NAME) }
}

internal fun updateDictionaryIfNeeded(
    context: Context,
    reason: DictionaryUpdateReason
): DictionaryUpdateResult {
    val prefs = dictionaryPreferences(context)
    val nowMs = System.currentTimeMillis()
    val lastCheckMs = prefs.getLong(KEY_DICTIONARY_LAST_CHECK, DEFAULT_LAST_CHECK_MS)
    if (reason == DictionaryUpdateReason.Scheduled && !isDictionaryUpdateDue(nowMs, lastCheckMs)) {
        return DictionaryUpdateResult.Skipped
    }
    val result = downloadDictionary(context, prefs)
    val editor = prefs.edit()
        .putLong(KEY_DICTIONARY_LAST_CHECK, nowMs)
    if (result is DictionaryUpdateResult.Updated) {
        editor.putLong(KEY_DICTIONARY_LAST_SUCCESS, nowMs)
    }
    editor.apply()
    return result
}

internal fun isDictionaryUpdateDue(nowMs: Long, lastCheckMs: Long): Boolean {
    return nowMs - lastCheckMs >= DICTIONARY_UPDATE_INTERVAL_MS
}

private fun readDictionaryFile(file: File): List<String> {
    if (!file.exists()) {
        return emptyList()
    }
    return runCatching { loadWordList { file.inputStream() } }
        .getOrDefault(emptyList())
}

private fun downloadDictionary(
    context: Context,
    prefs: SharedPreferences
): DictionaryUpdateResult {
    val url = URL(DICTIONARY_URL)
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = DICTIONARY_CONNECT_TIMEOUT_MS
        readTimeout = DICTIONARY_READ_TIMEOUT_MS
        instanceFollowRedirects = true
        setRequestProperty(HEADER_USER_AGENT, USER_AGENT_VALUE)
        prefs.getString(KEY_DICTIONARY_ETAG, null)?.let { setRequestProperty(HEADER_IF_NONE_MATCH, it) }
        prefs.getString(KEY_DICTIONARY_LAST_MODIFIED, null)
            ?.let { setRequestProperty(HEADER_IF_MODIFIED_SINCE, it) }
    }

    return try {
        when (connection.responseCode) {
            HttpURLConnection.HTTP_NOT_MODIFIED -> DictionaryUpdateResult.NotModified
            HttpURLConnection.HTTP_OK -> handleDictionaryDownload(connection, context, prefs)
            else -> DictionaryUpdateResult.Failed(
                "Dictionary download failed with HTTP ${connection.responseCode}."
            )
        }
    } catch (exception: Exception) {
        DictionaryUpdateResult.Failed(
            "Dictionary download failed: ${exception.message ?: "unknown error"}."
        )
    } finally {
        connection.disconnect()
    }
}

private fun handleDictionaryDownload(
    connection: HttpURLConnection,
    context: Context,
    prefs: SharedPreferences
): DictionaryUpdateResult {
    val words = connection.inputStream.bufferedReader().useLines { lines ->
        loadWordListFromLines(lines)
    }
    if (words.isEmpty()) {
        return DictionaryUpdateResult.Failed("Downloaded dictionary is empty.")
    }
    if (!persistDictionary(context, words)) {
        return DictionaryUpdateResult.Failed("Failed to save dictionary.")
    }
    saveCacheHeaders(prefs, connection)
    return DictionaryUpdateResult.Updated(words)
}

private fun persistDictionary(context: Context, words: List<String>): Boolean {
    val tempFile = File(context.filesDir, DICTIONARY_TEMP_FILE_NAME)
    val finalFile = dictionaryOverrideFile(context)
    return runCatching {
        tempFile.writeText(words.joinToString(DICTIONARY_LINE_SEPARATOR), Charsets.UTF_8)
        tempFile.copyTo(finalFile, overwrite = true)
        tempFile.delete()
    }.isSuccess
}

private fun saveCacheHeaders(prefs: SharedPreferences, connection: HttpURLConnection) {
    val etag = connection.getHeaderField(HEADER_ETAG)
    val lastModified = connection.getHeaderField(HEADER_LAST_MODIFIED)
    val editor = prefs.edit()
    if (!etag.isNullOrBlank()) {
        editor.putString(KEY_DICTIONARY_ETAG, etag)
    }
    if (!lastModified.isNullOrBlank()) {
        editor.putString(KEY_DICTIONARY_LAST_MODIFIED, lastModified)
    }
    editor.apply()
}

private fun dictionaryPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences(DICTIONARY_PREFS_NAME, Context.MODE_PRIVATE)
}

private fun dictionaryOverrideFile(context: Context): File {
    return File(context.filesDir, DICTIONARY_OVERRIDE_FILE_NAME)
}
