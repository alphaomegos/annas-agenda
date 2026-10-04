package com.alphaomegos.annasagenda

import android.content.Intent
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
import kotlinx.coroutines.flow.MutableStateFlow
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

class MainActivity : AppCompatActivity() {

    private lateinit var vm: AppViewModel

    // A day the widget asked to open, until the navigation has opened it.
    private val openDayRequest = MutableStateFlow<Long?>(null)

    private fun takeOpenDayRequest(intent: Intent?) {
        val day = intent?.getLongExtra(EXTRA_OPEN_EPOCH_DAY, Long.MIN_VALUE) ?: return
        if (day != Long.MIN_VALUE) {
            openDayRequest.value = day
            // Once taken, not taken again by a rotation re-reading the intent.
            intent.removeExtra(EXTRA_OPEN_EPOCH_DAY)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vm = ViewModelProvider(this)[AppViewModel::class.java]
        if (savedInstanceState == null) takeOpenDayRequest(intent)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())

        setContent {
            val loaded by vm.isLoaded.collectAsState()
            val storageFailure by vm.storageFailure.collectAsState()
            val themeMode by vm.themeMode.collectAsState()

            AnnaAgendaTheme(themeMode = themeMode) {
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

                        else -> {
                            val openDay by openDayRequest.collectAsState()
                            AppNav(
                                vm = vm,
                                openDayRequest = openDay,
                                onOpenDayHandled = { openDayRequest.value = null },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeOpenDayRequest(intent)
    }

    override fun onStop() {
        super.onStop()

        // Whether it may run, and on what scope, is the view model's business:
        // this used to launch in lifecycleScope, which is cancelled on destroy,
        // and destroy follows onStop on every rotation.
        vm.writeAutoBackupInBackground()
    }
}
