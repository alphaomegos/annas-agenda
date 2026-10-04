package com.alphaomegos.annasagenda.model

import java.time.LocalDate
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.support.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

sealed interface Counter {
    val id: Long
    val title: String
}

data class DateRangeCounter(
    override val id: Long,
    override val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
) : Counter

data class ManualCounter(
    override val id: Long,
    override val title: String,
    val balance: Int,
) : Counter