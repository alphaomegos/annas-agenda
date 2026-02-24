package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.components.MenuTile
import com.alphaomegos.annasagenda.findActivity
import com.alphaomegos.annasagenda.setAppLanguage

object AppLanguages {
    @Suppress("SpellCheckingInspection")
    const val SR_LATN = "sr-Latn"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx.findActivity()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.choose_language)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MenuTile(
                    iconRes = R.drawable.ic_lang_en,
                    title = stringResource(R.string.language_english),
                    onClick = {
                        setAppLanguage(ctx, "en")
                        activity?.recreate()
                        onBack()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                MenuTile(
                    iconRes = R.drawable.ic_lang_ru,
                    title = stringResource(R.string.language_russian),
                    onClick = {
                        setAppLanguage(ctx, "ru")
                        activity?.recreate()
                        onBack()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MenuTile(
                    iconRes = R.drawable.ic_lang_sr_latn,
                    title = stringResource(R.string.language_serbian),
                    onClick = {
                        setAppLanguage(ctx, AppLanguages.SR_LATN)
                        activity?.recreate()
                        onBack()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                MenuTile(
                    iconRes = R.drawable.ic_lang_ki,
                    title = stringResource(R.string.language_kiribati),
                    onClick = {
                        setAppLanguage(ctx, "gil")
                        activity?.recreate()
                        onBack()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}