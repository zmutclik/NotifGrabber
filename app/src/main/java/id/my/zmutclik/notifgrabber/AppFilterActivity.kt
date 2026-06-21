package id.my.zmutclik.notifgrabber

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

class AppFilterActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: AppFilterAdapter
    private val selectedPackages = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_filter)

        prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)

        val radioGroup     = findViewById<RadioGroup>(R.id.filterModeGroup)
        val radioAll       = findViewById<RadioButton>(R.id.radioAll)
        val radioWhite     = findViewById<RadioButton>(R.id.radioWhitelist)
        val radioBlack     = findViewById<RadioButton>(R.id.radioBlacklist)
        val searchInput    = findViewById<EditText>(R.id.searchInput)
        val recyclerView   = findViewById<RecyclerView>(R.id.appRecyclerView)
        val selectedCount  = findViewById<TextView>(R.id.selectedCountText)
        val btnSelectAll   = findViewById<Button>(R.id.btnSelectAll)
        val btnDeselectAll = findViewById<Button>(R.id.btnDeselectAll)
        val btnSave        = findViewById<Button>(R.id.btnSaveFilter)

        // ── Restore mode ──
        val savedMode = prefs.getString(MainActivity.KEY_FILTER_MODE, "all") ?: "all"
        when (savedMode) {
            "whitelist" -> radioWhite.isChecked = true
            "blacklist" -> radioBlack.isChecked = true
            else        -> radioAll.isChecked = true
        }

        // ── Restore selected apps ──
        selectedPackages.addAll(
            prefs.getStringSet(MainActivity.KEY_FILTER_APPS, emptySet()) ?: emptySet()
        )
        updateCount(selectedCount)

        // ── Adapter ──
        adapter = AppFilterAdapter(selectedPackages) { count ->
            updateCount(selectedCount)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // ── Load apps di background thread ──
        thread {
            val pm = packageManager
            val apps = pm.getInstalledApplications(0)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || isUserApp(it) }
                .map { info ->
                    AppItem(
                        appName     = pm.getApplicationLabel(info).toString(),
                        packageName = info.packageName,
                        icon        = pm.getApplicationIcon(info)
                    )
                }
                .sortedBy { it.appName.lowercase() }

            runOnUiThread {
                adapter.submitList(apps)
            }
        }

        // ── Search ──
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // ── Select All / Deselect All ──
        btnSelectAll.setOnClickListener {
            adapter.selectAll()
        }
        btnDeselectAll.setOnClickListener {
            adapter.deselectAll()
        }

        // ── Mode radio group toggle visibility ──
        val updateListEnabled = {
            val enabled = radioGroup.checkedRadioButtonId != R.id.radioAll
            recyclerView.alpha = if (enabled) 1.0f else 0.4f
            searchInput.isEnabled = enabled
            btnSelectAll.isEnabled = enabled
            btnDeselectAll.isEnabled = enabled
        }
        radioGroup.setOnCheckedChangeListener { _, _ -> updateListEnabled() }
        updateListEnabled()

        // ── Simpan ──
        btnSave.setOnClickListener {
            val mode = when (radioGroup.checkedRadioButtonId) {
                R.id.radioWhitelist -> "whitelist"
                R.id.radioBlacklist -> "blacklist"
                else                -> "all"
            }
            prefs.edit()
                .putString(MainActivity.KEY_FILTER_MODE, mode)
                .putStringSet(MainActivity.KEY_FILTER_APPS, selectedPackages.toSet())
                .apply()

            Toast.makeText(this, "Filter tersimpan ($mode, ${selectedPackages.size} aplikasi)", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateCount(tv: TextView) {
        tv.text = "${selectedPackages.size} aplikasi terpilih"
    }

    /** Cek apakah app diinstall user (bukan system bawaan pabrik). */
    private fun isUserApp(info: ApplicationInfo): Boolean {
        return info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 ||
               info.flags and ApplicationInfo.FLAG_SYSTEM == 0
    }
}
