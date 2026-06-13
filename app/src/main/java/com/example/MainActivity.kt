package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.ChatScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        enableEdgeToEdge()

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.setOnlineStatus(true)
            } else if (event == Lifecycle.Event.ON_STOP) {
                viewModel.setOnlineStatus(false)
            }
        })

        setContent {
            MyApplicationTheme(darkTheme = true) { // Explicitly set dark theme to true as requested
                ChatScreen(viewModel = viewModel)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: MainActivity? = null

        fun getStaticContext(): Context {
            return instance?.applicationContext ?: throw IllegalStateException("MainActivity not initialized yet")
        }
    }
}
