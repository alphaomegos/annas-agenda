package com.alphaomegos.annasagenda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.app.AnnaAgendaTheme

/**
 * What the app reads from Health Connect, and why (19.10).
 *
 * Health Connect opens this from its own permission screen — the «privacy
 * policy» link every app reading health data must answer — so it is a page of
 * its own, not a screen inside the app: it has to make sense to someone who
 * came here from Health Connect's settings and never opened the app.
 */
class StepsPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnnaAgendaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(stringResource(R.string.steps_privacy_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.steps_privacy_body), style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { finish() }) { Text(stringResource(R.string.steps_privacy_close)) }
                    }
                }
            }
        }
    }
}
