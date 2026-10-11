package com.example.clipnotepad.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Lightweight index record. The actual text lives in its own file. */
data class NoteEntry(
    val id: Long,
    val timestamp: Long,
    val preview: String,
    val length: Int,
    val source: String
)

/**
 * Storage design (built for very large text, e.g. a whole book):
 *
 *  files/texts/<id>.txt   -> the full text of one entry (never loaded unless opened)
 *  files/index.jsonl      -> one JSON line per entry (id, ts, 180-char preview, length)
 *
 * The list UI only ever touches the tiny index, so it stays instant no matter
 * how much text is stored.
 */
class NoteStore private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val textsDir = File(appContext.filesDir, "texts").apply { if (!exists()) mkdirs() }
    private val indexFile = File(appContext.filesDir, "index.jsonl")

    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<NoteEntry>>(emptyList())
    val entries: StateFlow<List<NoteEntry>> = _entries.asStateFlow()

    private var nextId = 1L

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val list = ArrayList<NoteEntry>()
            if (indexFile.exists()) {
                indexFile.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) {
                            runCatching {
                                val o = JSONObject(line)
                                list += NoteEntry(
                                    id = o.getLong("id"),
                                    timestamp = o.getLong("ts"),
                                    preview = o.optString("pv", ""),
                                    length = o.optInt("len", 0),
                                    source = o.optString("src", "clipboard")
                                )
                            }
                        }
                    }
                }
            }
            list.sortByDescending { it.timestamp }
            _entries.value = list
            nextId = (list.maxOfOrNull { it.id } ?: 0L) + 1
        }
    }

    suspend fun addEntry(
        text: String,
        timestamp: Long = System.currentTimeMillis(),
        source: String = "clipboard"
    ): NoteEntry? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        mutex.withLock {
            val id = nextId++
            File(textsDir, "$id.txt").writeText(text)

            val preview = text.replace('\n', ' ').replace('\r', ' ').trim().take(180)
            val json = JSONObject().apply {
                put("id", id)
                put("ts", timestamp)
                put("pv", preview)
                put("len", text.length)
                put("src", source)
            }
            indexFile.appendText(json.toString() + "\n")

            val entry = NoteEntry(id, timestamp, preview, text.length, source)
            _entries.value = (listOf(entry) + _entries.value).sortedByDescending { it.timestamp }
            entry
        }
    }

    suspend fun readText(id: Long): String = withContext(Dispatchers.IO) {
        val f = File(textsDir, "$id.txt")
        if (f.exists()) f.readText() else ""
    }

    suspend fun deleteEntry(id: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(textsDir, "$id.txt").delete()
            val remaining = _entries.value.filterNot { it.id == id }
            rewriteIndex(remaining)
            _entries.value = remaining
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            textsDir.listFiles()?.forEach { it.delete() }
            indexFile.delete()
            _entries.value = emptyList()
            nextId = 1L
        }
    }

    private fun rewriteIndex(list: List<NoteEntry>) {
        val tmp = File(appContext.filesDir, "index.jsonl.tmp")
        tmp.bufferedWriter().use { w ->
            list.sortedBy { it.id }.forEach { e ->
                val o = JSONObject().apply {
                    put("id", e.id)
                    put("ts", e.timestamp)
                    put("pv", e.preview)
                    put("len", e.length)
                    put("src", e.source)
                }
                w.write(o.toString())
                w.newLine()
            }
        }
        tmp.copyTo(indexFile, overwrite = true)
        tmp.delete()
    }

    companion object {
        @Volatile private var instance: NoteStore? = null

        fun get(context: Context): NoteStore =
            instance ?: synchronized(this) {
                instance ?: NoteStore(context.applicationContext).also { instance = it }
            }
    }
}
