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

/** Steps' days, the profile, the forecast's method and norm, the steps range (moved out of AppViewModel unchanged, 29.10; see AppStateHost). */
interface StepsDaysActions : AppStateHost {
    // Health Connect is read again after some changes here; the view model reads it (29.10).
    fun refreshSteps()

    /* ---------------- steps (19.10) ---------------- */

    /** What a read from Health Connect brought: each day of it, a day with nothing as 0. */
    fun applyReadSteps(read: Map<LocalDate, Int>) {
        stateFlow.update { cur -> cur.copy(stepDays = stepDaysAfterRead(cur.stepDays, read)) }
    }

    /** A day's steps typed in by hand; no later read replaces them. */
    fun typeSteps(date: LocalDate, steps: Int) {
        stateFlow.update { cur -> cur.copy(stepDays = stepDaysWithTyped(cur.stepDays, date, steps)) }
    }

    /** A typed-in day taken back: Health Connect's count returns with the next read. */
    fun untypeSteps(date: LocalDate) {
        stateFlow.update { cur -> cur.copy(stepDays = stepDaysWithoutTyped(cur.stepDays, date)) }
        refreshSteps()
    }

    fun setDailyNorm(kcal: Int?) {
        stateFlow.update { cur -> cur.copy(dailyNormKcal = kcal?.takeIf { it in DAILY_NORM_RANGE }) }
    }

    fun setWeightForecastMethod(method: ForecastMethod) {
        stateFlow.update { cur -> if (cur.weightForecastMethod == method) cur else cur.copy(weightForecastMethod = method) }
    }

    /** The profile (28.10): sex, height and year of birth, one set for the whole app. */
    fun setProfile(sex: ProfileSex?, heightCm: Int?, birthYear: Int?) {
        stateFlow.update { cur ->
            cur.copy(
                profileSex = sex,
                stepsHeightCm = heightCm?.takeIf { it in STEPS_HEIGHT_RANGE_CM },
                profileBirthYear = birthYear?.takeIf { it in PROFILE_BIRTH_YEARS },
            )
        }
    }

    fun setStepsHeight(heightCm: Int?) {
        stateFlow.update { cur -> cur.copy(stepsHeightCm = heightCm?.takeIf { it in STEPS_HEIGHT_RANGE_CM }) }
    }

    fun setStepsRange(range: AnthropometryRange, custom: DateWindow?) {
        stateFlow.update { cur -> cur.copy(stepsRange = range, stepsCustomRange = custom ?: cur.stepsCustomRange) }
    }
}
