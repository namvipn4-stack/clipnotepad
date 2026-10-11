package com.example.clipnotepad

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.clipnotepad.data.NoteStore
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var noteStore: NoteStore
    private lateinit var listView: ListView
    private lateinit var detailView: TextView
    private lateinit var saveButton: Button
    private lateinit var overlayButton: Button
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        noteStore = NoteStore.get(applicationContext)

        listView = findViewById(R.id.noteList)
        detailView = findViewById(R.id.noteDetail)
        saveButton = findViewById(R.id.saveClipboardButton)
        overlayButton = findViewById(R.id.overlayToggleButton)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        saveButton.setOnClickListener {
            saveClipboard()
        }

        overlayButton.setOnClickListener {
            toggleOverlay()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val note = noteStore.entries.value.getOrNull(position) ?: return@setOnItemClickListener
            detailView.text = "Note #${note.id}\nPreview: ${note.preview}\n\nLength: ${note.length}\nSource: ${note.source}"
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                noteStore.entries.collect { notes ->
                    val items = notes.map { "#${it.id} • ${it.preview.take(50)}" }
                    adapter.clear()
                    adapter.addAll(items)
                    adapter.notifyDataSetChanged()
                    if (notes.isEmpty()) {
                        detailView.text = "No notes yet"
                    }
                }
            }
        }

        lifecycleScope.launch {
            noteStore.load()
        }
    }

    private fun saveClipboard() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()

        if (text.isNullOrBlank()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val saved = noteStore.addEntry(text, source = "manual")
            if (saved != null) {
                Toast.makeText(this@MainActivity, "Saved note #${saved.id}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "Allow display over other apps", Toast.LENGTH_LONG).show()
            return
        }

        startService(Intent(this, com.example.clipnotepad.ui.OverlayService::class.java))
        Toast.makeText(this, "Floating button is active", Toast.LENGTH_SHORT).show()
    }
}
