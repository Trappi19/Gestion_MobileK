package com.example.gestion_mobilek

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
            addView(searchInput)
            addView(listView)
        }

        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(root)
            .setPositiveButton("OK") { _, _ -> onConfirm(selected) }
            .setNegativeButton("Annuler", null)

        if (!neutralButtonText.isNullOrBlank() && onNeutral != null) {
            builder.setNeutralButton(neutralButtonText) { _, _ -> onNeutral() }
        }

        val dialog = builder.create()
        dialog.setOnShowListener { applyCheckedState() }
        dialog.show()
    }
}

