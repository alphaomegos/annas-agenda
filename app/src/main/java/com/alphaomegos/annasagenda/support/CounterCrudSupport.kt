package com.alphaomegos.annasagenda

import java.time.LocalDate

fun normalizeCounterTitleOrNull(title: String): String? =
    title.trim().takeIf { it.isNotEmpty() }

fun stateWithAddedCounter(state: AppState, counter: Counter): AppState =
    state.copy(counters = state.counters + counter)

fun countersWithUpdatedManualCounter(
    counters: List<Counter>,
    counterId: Long,
    title: String,
    balance: Int,
): List<Counter> {
    return counters.map { counter ->
        if (counter is ManualCounter && counter.id == counterId) {
            counter.copy(title = title, balance = balance)
        } else {
            counter
        }
    }
}

fun countersWithUpdatedDateRangeCounter(
    counters: List<Counter>,
    counterId: Long,
    title: String,
    startDate: LocalDate,
    endDate: LocalDate,
): List<Counter> {
    return counters.map { counter ->
        if (counter is DateRangeCounter && counter.id == counterId) {
            counter.copy(title = title, startDate = startDate, endDate = endDate)
        } else {
            counter
        }
    }
}

fun stateWithoutCounter(state: AppState, counterId: Long): AppState {
    val newCounters = state.counters.filterNot { it.id == counterId }
    val newTasks = state.tasks.map { task ->
        if (task.linkedManualCounterId == counterId) {
            task.copy(linkedManualCounterId = null)
        } else {
            task
        }
    }
    return state.copy(counters = newCounters, tasks = newTasks)
}