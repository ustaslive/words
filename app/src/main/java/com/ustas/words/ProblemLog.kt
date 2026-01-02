package com.ustas.words

import android.content.Context
import android.content.Intent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PROBLEM_LOG_FILE_NAME = "problems.log"
private const val PROBLEM_LOG_DIR_NAME = "logs"
private const val PROBLEM_LOG_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
private const val PROBLEM_LOG_SUBJECT_DATE_PATTERN = "yyyy-MM-dd"
private const val PROBLEM_LOG_MIME_TYPE = "text/plain"
private const val PROBLEM_LOG_ENTRY_SEPARATOR = " "
private const val PROBLEM_LOG_LINE_BREAK_REPLACEMENT = " "
private const val PROBLEM_LOG_SUBJECT_SUFFIX = " words problems"

fun appendProblemLogEntry(context: Context, type: String, description: String) {
    val timestamp = formatProblemLogTimestamp(Date())
    val line = buildProblemLogLine(timestamp, type, description)
    val file = ensureProblemLogFile(context)
    file.appendText(line + System.lineSeparator())
}

fun shareProblemLog(context: Context) {
    val file = ensureProblemLogFile(context)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = PROBLEM_LOG_MIME_TYPE
        putExtra(Intent.EXTRA_TEXT, file.readText())
        putExtra(Intent.EXTRA_SUBJECT, buildProblemLogSubject(Date()))
    }
    val chooser = Intent.createChooser(shareIntent, context.getString(R.string.problem_log_share_title))
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

fun resetProblemLog(context: Context) {
    val file = ensureProblemLogFile(context)
    file.writeText("")
}

private fun buildProblemLogLine(timestamp: String, type: String, description: String): String {
    val safeType = sanitizeLogPart(type)
    val safeDescription = sanitizeLogPart(description)
    return listOf(timestamp, safeType, safeDescription).joinToString(PROBLEM_LOG_ENTRY_SEPARATOR)
}

private fun sanitizeLogPart(value: String): String {
    return value
        .replace("\r", PROBLEM_LOG_LINE_BREAK_REPLACEMENT)
        .replace("\n", PROBLEM_LOG_LINE_BREAK_REPLACEMENT)
        .trim()
}

private fun formatProblemLogTimestamp(date: Date): String {
    val formatter = SimpleDateFormat(PROBLEM_LOG_DATE_TIME_PATTERN, Locale.US)
    return formatter.format(date)
}

private fun buildProblemLogSubject(date: Date): String {
    val formatter = SimpleDateFormat(PROBLEM_LOG_SUBJECT_DATE_PATTERN, Locale.US)
    return formatter.format(date) + PROBLEM_LOG_SUBJECT_SUFFIX
}

private fun ensureProblemLogFile(context: Context): File {
    val logDir = File(context.filesDir, PROBLEM_LOG_DIR_NAME)
    if (!logDir.exists()) {
        logDir.mkdirs()
    }
    val file = File(logDir, PROBLEM_LOG_FILE_NAME)
    if (!file.exists()) {
        file.createNewFile()
    }
    return file
}
