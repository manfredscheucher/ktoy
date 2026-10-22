package org.bytefred.ktoy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.bytefred.ktoy.ui.App
import org.bytefred.ktoy.ui.initPrefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initPrefs(this)
        setContent { App() }
    }
}
