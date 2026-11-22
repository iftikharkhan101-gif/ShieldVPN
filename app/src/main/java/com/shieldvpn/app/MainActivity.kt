package com.shieldvpn.app

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.StringReader
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private enum class UiState { OFF, CONNECTING, ON }

    private lateinit var app: App
    private lateinit var store: ServerStore
    private var servers = mutableListOf<Server>()
    private var state = UiState.OFF
    private var updatingUi = false
    private var statsJob: Job? = null

    private var dialogConfigField: EditText? = null
    private var dialogCountrySpinner: Spinner? = null

    private lateinit var powerBtn: ImageButton
    private lateinit var progress: ProgressBar
    private lateinit var vpnSwitch: SwitchMaterial
    private lateinit var statusTitle: TextView
    private lateinit var statusSub: TextView
    private lateinit var badge: TextView
    private lateinit var spinner: Spinner
    private lateinit var addBtn: Button
    private lateinit var removeBtn: Button
    private lateinit var timeTv: TextView
    private lateinit var downTv: TextView
    private lateinit var upTv: TextView

    private val red by lazy { getColor(R.color.off) }
    private val green by lazy { getColor(R.color.on) }
    private val yellow by lazy { getColor(R.color.wait) }

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) connect()
            else {
                toast("VPN permission is needed to connect")
                render(UiState.OFF)
            }
        }

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) readConfigFile(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        app = application as App
        store = ServerStore(this)
        servers = store.load()

        powerBtn = findViewById(R.id.powerBtn)
        progress = findViewById(R.id.progress)
        vpnSwitch = findViewById(R.id.vpnSwitch)
        statusTitle = findViewById(R.id.statusTitle)
        statusSub = findViewById(R.id.statusSub)
        badge = findViewById(R.id.badge)
        spinner = findViewById(R.id.serverSpinner)
        addBtn = findViewById(R.id.addBtn)
        removeBtn = findViewById(R.id.removeBtn)
        timeTv = findViewById(R.id.statTime)
        downTv = findViewById(R.id.statDown)
        upTv = findViewById(R.id.statUp)

        powerBtn.setOnClickListener { onToggle() }
        vpnSwitch.setOnCheckedChangeListener { _, _ -> if (!updatingUi) onToggle() }
        addBtn.setOnClickListener { showAddDialog() }
        removeBtn.setOnClickListener { confirmRemove() }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val s = servers.getOrNull(pos) ?: return
                if (s.id == store.selectedId) return
                store.selectedId = s.id
                if (state == UiState.ON) connect() else render(state)
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        app.tunnel.listener = { s ->
            runOnUiThread {
                // Fires if Android or another VPN app turns ours off.
                if (s == Tunnel.State.DOWN && state == UiState.ON) {
                    stopStats()
                    render(UiState.OFF)
                }
            }
        }

        refreshSpinner()
        render(UiState.OFF)

        // If the VPN was already running when the app was reopened, show it.
        lifecycleScope.launch {
            val current = withContext(Dispatchers.IO) {
                runCatching { app.backend.getState(app.tunnel) }.getOrDefault(Tunnel.State.DOWN)
            }
            if (current == Tunnel.State.UP) {
                if (app.connectedAt == 0L) app.connectedAt = System.currentTimeMillis()
                render(UiState.ON)
                startStats()
            }
        }
    }

    override fun onDestroy() {
        app.tunnel.listener = null
        super.onDestroy()
    }

    // ---------- Connect / disconnect ----------

    private fun onToggle() {
        when (state) {
            UiState.OFF -> requestConnect()
            UiState.ON -> disconnect()
            UiState.CONNECTING -> render(state) // ignore taps while connecting
        }
    }

    private fun requestConnect() {
        if (servers.isEmpty()) {
            render(UiState.OFF)
            toast("Add a server first")
            showAddDialog()
            return
        }
        val permIntent = VpnService.prepare(this)
        if (permIntent != null) vpnPermission.launch(permIntent) else connect()
    }

    private fun connect() {
        val server = selectedServer() ?: return
        render(UiState.CONNECTING)
        lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    val cfg = Config.parse(BufferedReader(StringReader(server.config)))
                    app.backend.setState(app.tunnel, Tunnel.State.UP, cfg)
                    null
                } catch (e: Exception) {
                    e
                }
            }
            if (error == null) {
                app.connectedAt = System.currentTimeMillis()
                render(UiState.ON)
                startStats()
            } else {
                stopStats()
                render(UiState.OFF)
                val reason = (error as? BackendException)?.reason?.name
                    ?: error.message ?: error.javaClass.simpleName
                toast("Connection failed: $reason")
            }
        }
    }

    private fun disconnect() {
        stopStats()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { app.backend.setState(app.tunnel, Tunnel.State.DOWN, null) }
            }
            app.connectedAt = 0L
            render(UiState.OFF)
        }
    }

    // ---------- UI ----------

    private fun render(s: UiState) {
        state = s
        updatingUi = true
        vpnSwitch.isChecked = s != UiState.OFF
        updatingUi = false

        val color = when (s) {
            UiState.OFF -> red
            UiState.CONNECTING -> yellow
            UiState.ON -> green
        }
        powerBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(getColor(R.color.card))
            setStroke(dp(6), color)
        }
        powerBtn.imageTintList = ColorStateList.valueOf(color)
        progress.visibility = if (s == UiState.CONNECTING) View.VISIBLE else View.INVISIBLE

        statusTitle.setTextColor(color)
        statusTitle.text = when (s) {
            UiState.OFF -> "Disconnected"
            UiState.CONNECTING -> "Connecting…"
            UiState.ON -> "Connected"
        }
        val srv = selectedServer()
        statusSub.text = when {
            s == UiState.OFF && servers.isEmpty() -> "Tap “+ Add server” to get started"
            s == UiState.OFF -> "Tap the button to connect"
            else -> srv?.let { "${it.flag}  ${it.country}" } ?: ""
        }
        badge.text = if (s == UiState.ON) "Protected" else "Not protected"
        badge.setTextColor(if (s == UiState.ON) green else getColor(R.color.muted))

        spinner.isEnabled = s != UiState.CONNECTING && servers.isNotEmpty()
        addBtn.isEnabled = s != UiState.CONNECTING
        removeBtn.isEnabled = s != UiState.CONNECTING && servers.isNotEmpty()

        if (s == UiState.OFF) {
            timeTv.text = "00:00:00"
            downTv.text = "0 B"
            upTv.text = "0 B"
        }
    }

    private fun refreshSpinner() {
        val labels = if (servers.isEmpty()) listOf("No servers yet")
        else servers.mapIndexed { i, s ->
            val dupes = servers.take(i).count { it.code == s.code }
            "${s.flag}  ${s.country}" + if (dupes > 0) " ${dupes + 1}" else ""
        }
        val adapter = ArrayAdapter(this, R.layout.spinner_item, labels)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinner.adapter = adapter

        if (servers.isNotEmpty()) {
            if (servers.none { it.id == store.selectedId }) store.selectedId = servers.first().id
            spinner.setSelection(servers.indexOfFirst { it.id == store.selectedId }, false)
        } else {
            store.selectedId = null
        }
    }

    private fun selectedServer(): Server? =
        servers.firstOrNull { it.id == store.selectedId } ?: servers.firstOrNull()

    private fun startStats() {
        statsJob?.cancel()
        statsJob = lifecycleScope.launch {
            while (isActive) {
                val stats = withContext(Dispatchers.IO) {
                    runCatching { app.backend.getStatistics(app.tunnel) }.getOrNull()
                }
                val secs = (System.currentTimeMillis() - app.connectedAt) / 1000
                timeTv.text = "%02d:%02d:%02d".format(secs / 3600, secs / 60 % 60, secs % 60)
                if (stats != null) {
                    downTv.text = bytes(stats.totalRx())
                    upTv.text = bytes(stats.totalTx())
                }
                delay(1000)
            }
        }
    }

    private fun stopStats() {
        statsJob?.cancel()
        statsJob = null
    }

    // ---------- Add / remove servers ----------

    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_server, null)
        val countrySp = view.findViewById<Spinner>(R.id.dlgCountry)
        val cfgField = view.findViewById<EditText>(R.id.dlgConfig)
        view.findViewById<Button>(R.id.dlgImport).setOnClickListener {
            pickFile.launch(arrayOf("*/*"))
        }

        val adapter = ArrayAdapter(
            this, R.layout.spinner_item,
            Countries.list.map { "${Countries.flagOf(it.first)}  ${it.second}" }
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        countrySp.adapter = adapter

        dialogConfigField = cfgField
        dialogCountrySpinner = countrySp

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Add server")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = cfgField.text.toString().trim()
                try {
                    Config.parse(BufferedReader(StringReader(text)))
                } catch (e: Exception) {
                    cfgField.error = "This is not a valid WireGuard config"
                    return@setOnClickListener
                }
                val (code, name) = Countries.list[countrySp.selectedItemPosition]
                val server = Server(UUID.randomUUID().toString(), code, name, text)
                servers.add(server)
                store.save(servers)
                store.selectedId = server.id
                refreshSpinner()
                render(state)
                dialog.dismiss()
                toast("${server.flag} $name added")
            }
        }
        dialog.setOnDismissListener {
            dialogConfigField = null
            dialogCountrySpinner = null
        }
        dialog.show()
    }

    private fun readConfigFile(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            dialogConfigField?.setText(text)
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = Countries.guessIndex(c.getString(0) ?: "")
                    if (idx >= 0) dialogCountrySpinner?.setSelection(idx)
                }
            }
        } catch (e: Exception) {
            toast("Could not read that file")
        }
    }

    private fun confirmRemove() {
        val server = selectedServer() ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove server?")
            .setMessage("${server.flag}  ${server.country} will be deleted from this phone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                val wasActive = state == UiState.ON
                servers.removeAll { it.id == server.id }
                store.save(servers)
                store.selectedId = null
                refreshSpinner()
                if (wasActive) disconnect() else render(state)
            }
            .show()
    }

    // ---------- Helpers ----------

    private fun bytes(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
        b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
        else -> "%.2f GB".format(b / 1073741824.0)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
