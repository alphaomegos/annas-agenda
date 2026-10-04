package com.alphaomegos.annasagenda.support

import java.time.DayOfWeek
import java.time.LocalDate
import com.alphaomegos.annasagenda.*
import com.alphaomegos.annasagenda.model.*
import com.alphaomegos.annasagenda.data.*
import com.alphaomegos.annasagenda.app.*

/**
 * One dish of the diet as a given day shows it.
 *
 * [eaten] is the food-log entry the tick wrote, or null while it is not
 * ticked. [canTick] is false for a day still ahead: what has not happened yet
 * cannot have been eaten. [faded] marks a past day's dish nobody ticked —
 * shown greyed, still tickable, for the breakfast written down a day late.
 */
data class DietDishOnDay(
    val item: DietItem,
    val eaten: FoodEntry?,
    val canTick: Boolean,
    val faded: Boolean,
)

/** A day ahead of [today] has nothing to tick yet; today and the past do. */
fun canTickDietOn(date: LocalDate, today: LocalDate): Boolean = !date.isAfter(today)

/**
 * The diet as [date] shows it, worked out rather than stored.
 *
 * The plan for the day of the week, each dish paired with what the log says
 * was eaten from it that day. Nothing is written at midnight and nothing has
 * to be: the "disappearing" of a past day's unticked dish is this function
 * leaving it out when [showPastUnticked] is off.
 *
 * A dish ticked and then taken out of the diet is **not** here — the plan no
 * longer has it — but its entry is still in the log, and [foodOutsideDiet]
 * shows it with everything else eaten that day. A past day therefore never
 * loses a meal because the diet changed.
 */
fun dietDishesOn(
    date: LocalDate,
    today: LocalDate,
    plan: Map<DayOfWeek, List<DietItem>>,
    log: List<FoodEntry>,
    showPastUnticked: Boolean,
): List<DietDishOnDay> {
    val dishes = plan[date.dayOfWeek].orEmpty()
    if (dishes.isEmpty()) return emptyList()

    val ticked = log
        .filter { it.date == date && it.dietItemId != null }
        .groupBy { it.dietItemId!! }

    val past = date.isBefore(today)
    val tickable = canTickDietOn(date, today)

    return dishes.mapNotNull { item ->
        val eaten = ticked[item.id]?.minByOrNull { it.id }
        when {
            eaten != null -> DietDishOnDay(item, eaten, canTick = tickable, faded = false)
            past && !showPastUnticked -> null
            else -> DietDishOnDay(item, null, canTick = tickable, faded = past)
        }
    }
}

/**
 * What else was eaten on [date]: everything in the log except the entries
 * already shown as ticked dishes, so nothing appears twice.
 */
fun foodOutsideDiet(date: LocalDate, log: List<FoodEntry>, dishes: List<DietDishOnDay>): List<FoodEntry> {
    val shown = dishes.mapNotNullTo(mutableSetOf()) { it.eaten?.id }
    return log.filter { it.date == date && it.id !in shown }.sortedBy { it.id }
}

/**
 * Ticking a dish: an ordinary entry in the log, carrying the dish's id.
 *
 * The title and the kilocalories are copied, not referred to — the entry is
 * what was eaten, and changing the dish later changes no past day. Ticking a
 * dish already ticked that day, or a day ahead, changes nothing.
 */
fun foodLogAfterTickingDish(
    log: List<FoodEntry>,
    item: DietItem,
    date: LocalDate,
    today: LocalDate,
    newId: () -> Long,
): List<FoodEntry> {
    if (!canTickDietOn(date, today)) return log
    if (log.any { it.date == date && it.dietItemId == item.id }) return log

    return log + FoodEntry(
        id = newId(),
        date = date,
        title = item.title,
        kcal = item.kcal.coerceAtLeast(0),
        dietItemId = item.id,
    )
}

/** Unticking takes back what the tick wrote that day, and nothing else. */
fun foodLogAfterUntickingDish(log: List<FoodEntry>, itemId: Long, date: LocalDate): List<FoodEntry> {
    val after = log.filterNot { it.date == date && it.dietItemId == itemId }
    return if (after.size == log.size) log else after
}

/* ---------------- editing the plan ---------------- */

/** A dish added at the end of [day]. A blank title adds nothing. */
fun dietPlanAfterAdding(
    plan: Map<DayOfWeek, List<DietItem>>,
    day: DayOfWeek,
    title: String,
    kcal: Int,
    newId: () -> Long,
): Map<DayOfWeek, List<DietItem>> {
    val t = title.trim()
    if (t.isEmpty()) return plan
    val item = DietItem(id = newId(), title = t, kcal = kcal.coerceAtLeast(0))
    return plan + (day to (plan[day].orEmpty() + item))
}

/** The wording or the kilocalories of one dish; past entries keep their own. */
fun dietPlanAfterEditing(
    plan: Map<DayOfWeek, List<DietItem>>,
    itemId: Long,
    title: String,
    kcal: Int,
): Map<DayOfWeek, List<DietItem>> {
    val t = title.trim()
    if (t.isEmpty()) return plan
    var changed = false
    val next = plan.mapValues { (_, items) ->
        items.map {
            if (it.id == itemId && (it.title != t || it.kcal != kcal.coerceAtLeast(0))) {
                changed = true
                it.copy(title = t, kcal = kcal.coerceAtLeast(0))
            } else {
                it
            }
        }
    }
    return if (changed) next else plan
}

/** A dish out of the diet. A day left empty leaves the map. */
fun dietPlanAfterRemoving(plan: Map<DayOfWeek, List<DietItem>>, itemId: Long): Map<DayOfWeek, List<DietItem>> {
    if (plan.values.none { items -> items.any { it.id == itemId } }) return plan
    return plan
        .mapValues { (_, items) -> items.filterNot { it.id == itemId } }
        .filterValues { it.isNotEmpty() }
}

/** One step up (-1) or down (+1) within its day; the ends do not move. */
fun dietPlanAfterMoving(plan: Map<DayOfWeek, List<DietItem>>, itemId: Long, step: Int): Map<DayOfWeek, List<DietItem>> {
    val (day, items) = plan.entries.firstOrNull { (_, items) -> items.any { it.id == itemId } }
        ?.let { it.key to it.value } ?: return plan
    val from = items.indexOfFirst { it.id == itemId }
    val to = from + step
    if (to !in items.indices || to == from) return plan
    val moved = items.toMutableList().apply { add(to, removeAt(from)) }
    return plan + (day to moved)
}

/**
 * [from]'s dishes put on each of [to], **replacing** what those days had.
 *
 * Every copy gets an id of its own. Sharing one would tie two days together:
 * ticking Monday's porridge would tick Tuesday's, and taking it off Tuesday
 * would take it off Monday. Copying a day onto itself is skipped.
 */
fun dietPlanAfterCopyingDay(
    plan: Map<DayOfWeek, List<DietItem>>,
    from: DayOfWeek,
    to: Set<DayOfWeek>,
    newId: () -> Long,
): Map<DayOfWeek, List<DietItem>> {
    val source = plan[from].orEmpty()
    val targets = to - from
    if (targets.isEmpty()) return plan

    val next = plan.toMutableMap()
    DayOfWeek.entries.filter { it in targets }.forEach { day ->
        if (source.isEmpty()) {
            next.remove(day)
        } else {
            next[day] = source.map { it.copy(id = newId()) }
        }
    }
    return next
}

/** What the plan for [day] adds up to. */
fun dietKcalFor(plan: Map<DayOfWeek, List<DietItem>>, day: DayOfWeek): Int =
    plan[day].orEmpty().sumOf { it.kcal }
