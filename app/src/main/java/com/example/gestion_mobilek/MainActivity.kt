package com.example.gestion_mobilek

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Écouteurs sur les boutons
        findViewById<Button>(R.id.btnPlats).setOnClickListener {
            // TODO: Ouvrir liste des plats
        }

        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener {
            // TODO: Ouvrir menu (PopupMenu)
        }
    }
}
