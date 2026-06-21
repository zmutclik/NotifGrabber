package id.my.zmutclik.notifgrabber

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Menyimpan log pengiriman notifikasi ke SharedPreferences.
 * Maksimal [MAX_ENTRIES] entri terbaru yang disimpan.
 */
object LogManager {

    private const val PREFS_LOG   = "notifgrabber_log"
    private const val KEY_ENTRIES = "log_entries"
    const val MAX_ENTRIES         = 100

    private val sdf = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())

    data class LogEntry(
        val time:      String,
        val event:     String,   // "posted" | "removed" | "test"
        val appName:   String,
        val title:     String,
        val success:   Boolean?,  // null = belum ada hasil (fire-and-forget)
        val httpInfo:  String     // "HTTP 200", "network error", dll.
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("time",     time)
            put("event",    event)
            put("app_name", appName)
            put("title",    title)
            put("success",  success ?: JSONObject.NULL)
            put("http_info", httpInfo)
        }

        companion object {
            fun fromJson(o: JSONObject) = LogEntry(
                time     = o.optString("time"),
                event    = o.optString("event"),
                appName  = o.optString("app_name"),
                title    = o.optString("title"),
                success  = if (o.isNull("success")) null else o.optBoolean("success"),
                httpInfo = o.optString("http_info")
            )
        }
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_LOG, Context.MODE_PRIVATE)

    /** Tambah satu entri log; entri lama dihapus jika melebihi [MAX_ENTRIES]. */
    fun add(ctx: Context, entry: LogEntry) {
        val p    = prefs(ctx)
        val arr  = load(p)
        arr.add(0, entry)                          // terbaru di atas
        while (arr.size > MAX_ENTRIES) arr.removeLast()
        save(p, arr)
    }

    /** Perbarui entri paling atas (untuk mengisi hasil HTTP setelah fire-and-forget). */
    fun updateLatest(ctx: Context, success: Boolean, httpInfo: String) {
        val p   = prefs(ctx)
        val arr = load(p)
        if (arr.isEmpty()) return
        val old = arr[0]
        arr[0]  = old.copy(success = success, httpInfo = httpInfo)
        save(p, arr)
    }

    /** Ambil semua entri log (terbaru di indeks 0). */
    fun getAll(ctx: Context): List<LogEntry> = load(prefs(ctx))

    /** Hapus semua log. */
    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_ENTRIES).apply()
    }

    fun nowString(): String = sdf.format(Date())

    // ── internal ──────────────────────────────────────────────────────────────

    private fun load(p: SharedPreferences): MutableList<LogEntry> {
        val raw = p.getString(KEY_ENTRIES, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { LogEntry.fromJson(arr.getJSONObject(it)) }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
    }

    private fun save(p: SharedPreferences, list: List<LogEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        p.edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }
}
