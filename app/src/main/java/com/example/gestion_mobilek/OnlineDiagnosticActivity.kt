package com.example.gestion_mobilek

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class OnlineDiagnosticActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online_diagnostic)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val tvStatus = findViewById<TextView>(R.id.tvDiagnosticStatus)
        val tvContent = findViewById<TextView>(R.id.tvDiagnosticContent)
        val btnRefresh = findViewById<Button>(R.id.btnRefreshDiagnostic)

        fun refresh() {
            tvStatus.text = getString(R.string.online_diagnostic_loading)
            tvContent.text = ""
            btnRefresh.isEnabled = false

            Thread {
                val result = ExternalMariaDbSync.buildOnlineDiagnostic(applicationContext)
                runOnUiThread {
                    btnRefresh.isEnabled = true
                    result
                        .onSuccess { report ->
                            tvStatus.text = getString(R.string.online_diagnostic_ready)
                            tvContent.text = formatReport(report)
                        }
                        .onFailure { error ->
                            tvStatus.text = getString(R.string.online_diagnostic_error)
                            tvContent.text = errorMessage(error)
                            Toast.makeText(this, error.message ?: getString(R.string.online_diagnostic_error), Toast.LENGTH_LONG).show()
                        }
                }
            }.start()
        }

        btnRefresh.setOnClickListener { refresh() }
        refresh()
    }

    private fun errorMessage(error: Throwable): String {
        return buildString {
            appendLine(getString(R.string.online_diagnostic_error))
            appendLine()
            appendLine(error.message ?: getString(R.string.online_diagnostic_error_unknown))
        }
    }

    private fun formatReport(report: ExternalMariaDbSync.OnlineDiagnosticReport): String {
        return buildString {
            appendLine(getString(R.string.online_diagnostic_host, report.host, report.port))
            appendLine(getString(R.string.online_diagnostic_database, report.resolvedDatabase))
            appendLine(getString(R.string.online_diagnostic_configured_database, report.configuredDatabase ?: getString(R.string.online_diagnostic_none)))
            appendLine()
            report.notes.forEach { note ->
                appendLine("• $note")
            }
            appendLine()
            report.tables.forEach { table ->
                appendLine(getString(R.string.online_diagnostic_table_title, table.localTable, table.remoteTable ?: getString(R.string.online_diagnostic_table_missing)))
                appendLine(getString(R.string.online_diagnostic_columns_local, joinOrNone(table.localColumns)))
                appendLine(getString(R.string.online_diagnostic_columns_remote, joinOrNone(table.remoteColumns)))
                appendLine(getString(R.string.online_diagnostic_columns_mapped, if (table.mappedColumns.isEmpty()) getString(R.string.online_diagnostic_none) else table.mappedColumns.joinToString(", ") { "${it.localColumn} → ${it.remoteColumn}" }))
                appendLine(getString(R.string.online_diagnostic_columns_ignored_local, joinOrNone(table.ignoredLocalColumns)))
                appendLine(getString(R.string.online_diagnostic_columns_ignored_remote, joinOrNone(table.ignoredRemoteColumns)))
                appendLine()
            }
        }.trimEnd()
    }

    private fun joinOrNone(items: List<String>): String {
        return if (items.isEmpty()) getString(R.string.online_diagnostic_none) else items.joinToString(", ")
    }
}

