package com.ustas.words

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val DICTIONARY_ASSET_NAME = "words.txt"
private const val MODE005_STATS_ASSET_NAME = "005.stat.txt"
private const val FORBIDDEN_WORDS_ASSET_NAME = "forbidden_words.txt"
private const val DICTIONARY_OVERRIDE_FILE_NAME = "words_override.txt"
private const val DICTIONARY_TEMP_FILE_NAME = "words_override.tmp"
private const val MODE005_STATS_OVERRIDE_FILE_NAME = "005_stat_override.txt"
private const val MODE005_STATS_TEMP_FILE_NAME = "005_stat_override.tmp"
private const val DICTIONARY_PREFS_NAME = "words_settings"
private const val KEY_DICTIONARY_ETAG = "dictionary_etag"
private const val KEY_DICTIONARY_LAST_MODIFIED = "dictionary_last_modified"
private const val KEY_MODE005_STATS_ETAG = "mode005_stats_etag"
private const val KEY_MODE005_STATS_LAST_MODIFIED = "mode005_stats_last_modified"
private const val KEY_DICTIONARY_LAST_CHECK = "dictionary_last_check"
private const val KEY_DICTIONARY_LAST_SUCCESS = "dictionary_last_success"
private const val DEFAULT_LAST_CHECK_MS = 0L
private const val UNKNOWN_UPDATE_TIME_MS = 0L
private const val PACKAGE_INFO_FLAGS = 0L
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
private const val DICTIONARY_URL = "https://raw.githubusercontent.com/ustaslive/words/develop/app/src/main/assets/words.txt"
private const val MODE005_STATS_URL = "https://raw.githubusercontent.com/ustaslive/words/develop/lab/crossword_repeatability/005.stat.txt"
private const val HTTP_RESPONSE_NOT_MODIFIED = HttpURLConnection.HTTP_NOT_MODIFIED
private const val HTTP_RESPONSE_OK = HttpURLConnection.HTTP_OK
private const val EMPTY_CONTENT_LENGTH = 0

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

private sealed interface SupplementalUpdateResult {
    data object Updated : SupplementalUpdateResult
    data object NotModified : SupplementalUpdateResult
    data class Failed(val errorMessage: String) : SupplementalUpdateResult
}

internal fun loadDictionaryWords(context: Context): List<String> {
    val overrideFile = dictionaryOverrideFile(context)
    if (shouldDiscardOverride(context, overrideFile)) {
        overrideFile.delete()
    }
    val overrideWords = readDictionaryFile(overrideFile)
    val forbiddenWords = loadForbiddenWords(context)
    val dictionaryWords = if (overrideWords.isNotEmpty()) {
        overrideWords
    } else {
        loadWordList { context.assets.open(DICTIONARY_ASSET_NAME) }
    }
    return filterForbiddenWords(dictionaryWords, forbiddenWords)
}

internal fun loadMode005WordStats(context: Context): Mode005WordStats {
    val overrideFile = mode005StatsOverrideFile(context)
    if (shouldDiscardOverride(context, overrideFile)) {
        overrideFile.delete()
    }
    val overrideStats = readMode005WordStatsFile(overrideFile)
    if (overrideStats != null) {
        return overrideStats
    }
    return runCatching {
        context.assets.open(MODE005_STATS_ASSET_NAME).bufferedReader().useLines { lines ->
            parseMode005WordStats(lines)
        }
    }.getOrElse { emptyMode005WordStats() }
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
    val dictionaryResult = downloadDictionary(context, prefs)
    val mode005Result = downloadMode005Stats(context, prefs)
    val result = combineUpdateResults(context, dictionaryResult, mode005Result)
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

private fun readMode005WordStatsFile(file: File): Mode005WordStats? {
    if (!file.exists()) {
        return null
    }
    return runCatching {
        file.bufferedReader().useLines { lines ->
            parseMode005WordStats(lines)
        }
    }.getOrNull()
}

private fun shouldDiscardOverride(context: Context, overrideFile: File): Boolean {
    if (!overrideFile.exists()) {
        return false
    }
    val lastUpdateTimeMs = getAppLastUpdateTime(context)
    if (lastUpdateTimeMs == UNKNOWN_UPDATE_TIME_MS) {
        return false
    }
    return overrideFile.lastModified() < lastUpdateTimeMs
}

private fun getAppLastUpdateTime(context: Context): Long {
    return runCatching {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PACKAGE_INFO_FLAGS)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PACKAGE_INFO_FLAGS.toInt())
        }
        packageInfo.lastUpdateTime
    }.getOrDefault(UNKNOWN_UPDATE_TIME_MS)
}

private fun loadForbiddenWords(context: Context): Set<String> {
    return runCatching { loadWordList { context.assets.open(FORBIDDEN_WORDS_ASSET_NAME) } }
        .getOrDefault(emptyList())
        .toSet()
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
            HTTP_RESPONSE_NOT_MODIFIED -> DictionaryUpdateResult.NotModified
            HTTP_RESPONSE_OK -> handleDictionaryDownload(connection, context, prefs)
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

private fun downloadMode005Stats(
    context: Context,
    prefs: SharedPreferences
): SupplementalUpdateResult {
    val url = URL(MODE005_STATS_URL)
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = DICTIONARY_CONNECT_TIMEOUT_MS
        readTimeout = DICTIONARY_READ_TIMEOUT_MS
        instanceFollowRedirects = true
        setRequestProperty(HEADER_USER_AGENT, USER_AGENT_VALUE)
        prefs.getString(KEY_MODE005_STATS_ETAG, null)?.let { setRequestProperty(HEADER_IF_NONE_MATCH, it) }
        prefs.getString(KEY_MODE005_STATS_LAST_MODIFIED, null)
            ?.let { setRequestProperty(HEADER_IF_MODIFIED_SINCE, it) }
    }

    return try {
        when (connection.responseCode) {
            HTTP_RESPONSE_NOT_MODIFIED -> SupplementalUpdateResult.NotModified
            HTTP_RESPONSE_OK -> handleMode005StatsDownload(connection, context, prefs)
            else -> SupplementalUpdateResult.Failed(
                "Mode 005 stats download failed with HTTP ${connection.responseCode}."
            )
        }
    } catch (exception: Exception) {
        SupplementalUpdateResult.Failed(
            "Mode 005 stats download failed: ${exception.message ?: "unknown error"}."
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
    val forbiddenWords = loadForbiddenWords(context)
    val filteredWords = filterForbiddenWords(words, forbiddenWords)
    if (filteredWords.isEmpty()) {
        return DictionaryUpdateResult.Failed("Downloaded dictionary is empty.")
    }
    if (!persistDictionary(context, filteredWords)) {
        return DictionaryUpdateResult.Failed("Failed to save dictionary.")
    }
    saveCacheHeaders(
        prefs = prefs,
        connection = connection,
        etagKey = KEY_DICTIONARY_ETAG,
        lastModifiedKey = KEY_DICTIONARY_LAST_MODIFIED
    )
    return DictionaryUpdateResult.Updated(filteredWords)
}

private fun handleMode005StatsDownload(
    connection: HttpURLConnection,
    context: Context,
    prefs: SharedPreferences
): SupplementalUpdateResult {
    val rawStatsText = connection.inputStream.bufferedReader().use { reader ->
        reader.readText()
    }
    if (rawStatsText.isBlank() || rawStatsText.length <= EMPTY_CONTENT_LENGTH) {
        return SupplementalUpdateResult.Failed("Downloaded mode 005 stats are empty.")
    }
    val parsedStats = runCatching {
        parseMode005WordStats(rawStatsText.lineSequence())
    }.getOrElse { exception ->
        return SupplementalUpdateResult.Failed(
            "Downloaded mode 005 stats are invalid: ${exception.message ?: "parse error"}."
        )
    }
    if (parsedStats.frequencies.isEmpty()) {
        return SupplementalUpdateResult.Failed("Downloaded mode 005 stats are empty.")
    }
    if (!persistMode005Stats(context, rawStatsText)) {
        return SupplementalUpdateResult.Failed("Failed to save mode 005 stats.")
    }
    saveCacheHeaders(
        prefs = prefs,
        connection = connection,
        etagKey = KEY_MODE005_STATS_ETAG,
        lastModifiedKey = KEY_MODE005_STATS_LAST_MODIFIED
    )
    return SupplementalUpdateResult.Updated
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

private fun persistMode005Stats(context: Context, rawStatsText: String): Boolean {
    val tempFile = File(context.filesDir, MODE005_STATS_TEMP_FILE_NAME)
    val finalFile = mode005StatsOverrideFile(context)
    return runCatching {
        tempFile.writeText(rawStatsText, Charsets.UTF_8)
        tempFile.copyTo(finalFile, overwrite = true)
        tempFile.delete()
    }.isSuccess
}

private fun combineUpdateResults(
    context: Context,
    dictionaryResult: DictionaryUpdateResult,
    mode005Result: SupplementalUpdateResult
): DictionaryUpdateResult {
    if (dictionaryResult is DictionaryUpdateResult.Failed) {
        return dictionaryResult
    }
    if (dictionaryResult is DictionaryUpdateResult.Updated) {
        return dictionaryResult
    }
    return when (mode005Result) {
        SupplementalUpdateResult.Updated -> DictionaryUpdateResult.Updated(loadDictionaryWords(context))
        SupplementalUpdateResult.NotModified -> DictionaryUpdateResult.NotModified
        is SupplementalUpdateResult.Failed -> DictionaryUpdateResult.Failed(mode005Result.errorMessage)
    }
}

private fun saveCacheHeaders(
    prefs: SharedPreferences,
    connection: HttpURLConnection,
    etagKey: String,
    lastModifiedKey: String
) {
    val etag = connection.getHeaderField(HEADER_ETAG)
    val lastModified = connection.getHeaderField(HEADER_LAST_MODIFIED)
    val editor = prefs.edit()
    if (!etag.isNullOrBlank()) {
        editor.putString(etagKey, etag)
    }
    if (!lastModified.isNullOrBlank()) {
        editor.putString(lastModifiedKey, lastModified)
    }
    editor.apply()
}

private fun dictionaryPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences(DICTIONARY_PREFS_NAME, Context.MODE_PRIVATE)
}

private fun dictionaryOverrideFile(context: Context): File {
    return File(context.filesDir, DICTIONARY_OVERRIDE_FILE_NAME)
}

private fun mode005StatsOverrideFile(context: Context): File {
    return File(context.filesDir, MODE005_STATS_OVERRIDE_FILE_NAME)
}
