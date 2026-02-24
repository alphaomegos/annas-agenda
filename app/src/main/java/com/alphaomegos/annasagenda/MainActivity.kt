package com.alphaomegos.annasagenda

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.alphaomegos.annasagenda.util.writeBackupToDocuments
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var vm: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vm = ViewModelProvider(this)[AppViewModel::class.java]

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())

        setContent {
            val loaded by vm.isLoaded.collectAsState()

            AnnaAgendaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!loaded) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        AppNav(vm)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()

        // don't write backup until initial state is loaded
        if (!vm.isLoaded.value) return

        lifecycleScope.launch {
            val json = vm.exportBackupJson()
            writeBackupToDocuments(
                context = applicationContext,
                json = json
            )
        }
    }
}
