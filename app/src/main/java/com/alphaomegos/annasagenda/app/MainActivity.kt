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
import com.alphaomegos.annasagenda.screens.StorageFailureScreen

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
            val storageFailure by vm.storageFailure.collectAsState()

            AnnaAgendaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val failure = storageFailure
                    when {
                        !loaded -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }

                        failure != null -> StorageFailureScreen(
                            failure = failure,
                            onContinueEmpty = { vm.discardCorruptedStateAndStartEmpty() }
                        )

                        else -> AppNav(vm)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()

        // Whether it may run, and on what scope, is the view model's business:
        // this used to launch in lifecycleScope, which is cancelled on destroy,
        // and destroy follows onStop on every rotation.
        vm.writeAutoBackupInBackground()
    }
}
