package com.alphaomegos.annasagenda.app

import android.app.Application
import android.net.Uri
import com.alphaomegos.annasagenda.util.collectInternalCoverRefs
import com.alphaomegos.annasagenda.util.deleteInternalCoverIfAny
import com.alphaomegos.annasagenda.util.importCoverIntoInternalStorage
import com.alphaomegos.annasagenda.util.isInternalCoverRef
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*

/** The look: the day screen's style, the main menu's, Back, the palette, the theme (moved out of AppViewModel unchanged, 29.10; see AppStateHost). */
interface LookActions : AppStateHost {
    fun setDayScreenStyle(style: DayScreenStyle) {
        stateFlow.update { cur -> if (cur.dayScreenStyle == style) cur else cur.copy(dayScreenStyle = style) }
    }

    fun setMainMenuNarrow(style: MainMenuNarrowStyle) {
        stateFlow.update { cur -> if (cur.mainMenuNarrow == style) cur else cur.copy(mainMenuNarrow = style) }
    }

    fun setBackStyle(style: BackStyle) {
        if (stateFlow.value.backStyle == style) return
        stateFlow.value = stateFlow.value.copy(backStyle = style)
    }

    fun setPalette(palette: AppPalette) {
        if (stateFlow.value.palette == palette) return
        stateFlow.value = stateFlow.value.copy(palette = palette)
    }

    fun setThemeMode(mode: AppThemeMode) {
        if (stateFlow.value.themeMode == mode) return
        stateFlow.value = stateFlow.value.copy(themeMode = mode)
    }

}
