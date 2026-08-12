package com.wellnesscompanion.app.ui.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wellnesscompanion.app.data.local.entity.EntryEntity
import com.wellnesscompanion.app.data.model.CycleData
import com.wellnesscompanion.app.data.repository.EntryRepository
import com.wellnesscompanion.app.util.fromJsonSafe
import com.wellnesscompanion.app.util.todayDateString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class CycleStats(
    val avgCycleLength: Int? = null,
    val avgPeriodLength: Int? = null,
    val regularity: Int? = null,
    val cycleCount: Int = 0,
    val nextPeriod: LocalDate? = null,
    val nextOvulation: LocalDate? = null
)

@HiltViewModel
class CycleViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val gson: Gson
) : ViewModel() {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Load last 365 days of cycle entries
    private val startDate = LocalDate.now().minusDays(365).format(fmt)
    private val endDate = LocalDate.now().format(fmt)

    private val allCycleEntries = repository.getEntriesByDateRange(startDate, endDate, "cycle")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayEntries = repository.getTodayEntries("cycle")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val flowDates = allCycleEntries.map { entries ->
        entries.map { it.date }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val todayFlow = todayEntries.map { entries ->
        entries.lastOrNull()?.let { gson.fromJsonSafe<CycleData>(it.data) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val stats = flowDates.map { dates -> computeStats(dates) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CycleStats())

    private val _calMonth = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    val calMonth = _calMonth.asStateFlow()

    fun prevMonth() { _calMonth.value = _calMonth.value.minusMonths(1) }
    fun nextMonth() { _calMonth.value = _calMonth.value.plusMonths(1) }

    fun toggleFlow(flow: String) {
        viewModelScope.launch {
            val current = todayFlow.value
            val todayRaw = todayEntries.value
            // Delete existing entries for today
            for (e in todayRaw) {
                repository.deleteEntry(e)
            }
            // If different flow, add new entry
            if (current?.flow != flow) {
                repository.addEntry("cycle", CycleData(flow = flow))
            }
        }
    }

    fun toggleDateFlow(date: LocalDate) {
        viewModelScope.launch {
            val dateStr = date.format(fmt)
            val entries = repository.getEntriesByDateAndCategory(dateStr, "cycle").first()
            // Delete existing entries for this date
            for (e in entries) {
                repository.deleteEntry(e)
            }
            // If no entries existed, add medium flow
            if (entries.isEmpty()) {
                repository.addEntryForDate("cycle", CycleData(flow = "medium"), dateStr)
            }
        }
    }

    private fun computeStats(flowDateStrings: Set<String>): CycleStats {
        if (flowDateStrings.isEmpty()) return CycleStats()

        val sorted = flowDateStrings.map { LocalDate.parse(it, fmt) }.sorted()

        // Group consecutive dates into period spans (allow 1-day gap)
        val periods = mutableListOf<Pair<LocalDate, LocalDate>>()
        var spanStart = sorted[0]
        var spanEnd = sorted[0]
        for (i in 1 until sorted.size) {
            val daysBetween = ChronoUnit.DAYS.between(spanEnd, sorted[i])
            if (daysBetween <= 2) {
                spanEnd = sorted[i]
            } else {
                periods.add(spanStart to spanEnd)
                spanStart = sorted[i]
                spanEnd = sorted[i]
            }
        }
        periods.add(spanStart to spanEnd)

        // Period lengths
        val periodLengths = periods.map { ChronoUnit.DAYS.between(it.first, it.second).toInt() + 1 }
        val avgPeriodLength = if (periodLengths.isNotEmpty()) periodLengths.average().roundToInt() else null

        // Cycle lengths (start-to-start, filter outliers)
        val cycleLengths = mutableListOf<Int>()
        for (i in 1 until periods.size) {
            val len = ChronoUnit.DAYS.between(periods[i - 1].first, periods[i].first).toInt()
            if (len in 15..60) cycleLengths.add(len)
        }
        val avgCycleLength = if (cycleLengths.isNotEmpty()) cycleLengths.average().roundToInt() else null

        // Regularity score
        val regularity = if (cycleLengths.size >= 2 && avgCycleLength != null) {
            val variance = cycleLengths.map { (it - avgCycleLength).toDouble().let { d -> d * d } }.average()
            val stdDev = sqrt(variance)
            max(0, (100 - (stdDev / 7.0) * 100).roundToInt())
        } else null

        // Predictions
        val nextPeriod = if (avgCycleLength != null && periods.isNotEmpty()) {
            periods.last().first.plusDays(avgCycleLength.toLong())
        } else null

        val nextOvulation = if (avgCycleLength != null && periods.isNotEmpty()) {
            periods.last().first.plusDays((avgCycleLength - 14).toLong())
        } else null

        return CycleStats(avgCycleLength, avgPeriodLength, regularity, cycleLengths.size, nextPeriod, nextOvulation)
    }
}
