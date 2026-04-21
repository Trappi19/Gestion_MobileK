package com.example.gestion_mobilek.ui.common

import com.example.gestion_mobilek.R
import com.example.gestion_mobilek.app.*
import com.example.gestion_mobilek.data.*
import com.example.gestion_mobilek.reminders.*
import com.example.gestion_mobilek.sync.*
import com.example.gestion_mobilek.ui.common.*
import com.example.gestion_mobilek.ui.future.*
import com.example.gestion_mobilek.ui.history.*
import com.example.gestion_mobilek.ui.items.*
import com.example.gestion_mobilek.ui.main.*
import com.example.gestion_mobilek.ui.persons.*
import com.example.gestion_mobilek.ui.settings.*
import com.example.gestion_mobilek.utils.*

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView

object SearchableMultiSelectDialog {

    fun <T> show(
        context: Context,
        title: String,
        items: List<T>,
        labelOf: (T) -> String,
        initialSelection: Set<T>,
        neutralButtonText: String? = null,
        onNeutral: (() -> Unit)? = null,
        onConfirm: (Set<T>) -> Unit
    ) {
        val selected = initialSelection.toMutableSet()
        val filtered = items.toMutableList()

        val searchInput = EditText(context).apply {
            hint = "Rechercher..."
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val listView = ListView(context).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_list_item_multiple_choice,
            filtered.map(labelOf).toMutableList()
        )
        listView.adapter = adapter

        fun applyCheckedState() {
            for (index in filtered.indices) {
                listView.setItemChecked(index, selected.contains(filtered[index]))
            }
        }

        fun applyFilter(rawQuery: String) {
            val query = rawQuery.trim().lowercase()
            filtered.clear()
            if (query.isBlank()) {
                filtered.addAll(items)
            } else {
                filtered.addAll(items.filter { labelOf(it).lowercase().contains(query) })
            }

            adapter.clear()
            adapter.addAll(filtered.map(labelOf))
            adapter.notifyDataSetChanged()
            listView.post { applyCheckedState() }
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = filtered.getOrNull(position) ?: return@setOnItemClickListener
            if (selected.contains(item)) {
                selected.remove(item)
            } else {
                selected.add(item)
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(searchInput)
            addView(listView)
        }

        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(root)

        val dialog = builder.create()

        val buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        if (!neutralButtonText.isNullOrBlank() && onNeutral != null) {
            val btnNew = android.widget.Button(context).apply {
                text = neutralButtonText
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setTextColor(android.graphics.Color.parseColor("#4A90E2")) // Blue color
                setOnClickListener {
                    onNeutral()
                    dialog.dismiss()
                }
            }
            buttonContainer.addView(btnNew)
        }

        val spacer = android.view.View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        buttonContainer.addView(spacer)

        val btnCancel = android.widget.Button(context).apply {
            text = "Annuler"
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(android.graphics.Color.parseColor("#666666"))
            setOnClickListener { dialog.dismiss() }
        }
        buttonContainer.addView(btnCancel)

        val btnOk = android.widget.Button(context).apply {
            text = "OK"
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(android.graphics.Color.parseColor("#4A90E2")) // Blue color
            setOnClickListener {
                onConfirm(selected)
                dialog.dismiss()
            }
        }
        buttonContainer.addView(btnOk)

        root.addView(buttonContainer)

        dialog.setOnShowListener { applyCheckedState() }
        dialog.show()

        // Ensure ListView takes remaining space but doesn't push buttons out
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
