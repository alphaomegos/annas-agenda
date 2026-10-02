package com.alphaomegos.annasagenda

import java.time.LocalDate

/**
 * Something the user has written before, offered back while they write it
 * again.
 *
 * [subtaskDescriptions] comes from the most recent task with this wording,
 * because that is the version they last thought was right. Offering the parts
 * is the point of the whole feature: retyping "Починить колесо" is a
 * annoyance, retyping its seven parts is why people stop breaking tasks down.
 */
data class TaskSuggestion(
    val description: String,
    val subtaskDescriptions: List<String>,
    val timesUsed: Int,
    val lastUsedOn: LocalDate?,
)

/**
 * What the user has written before that looks like what they are writing now.
 *
 * **Generated repeats are left out of the pool entirely.** A daily task has
 * four hundred copies of itself in the history, and counting them would put
 * "Зарядка" at the top of every list for ever while burying the thing typed
 * three times on purpose. The template is in the pool and carries the same
 * wording, so nothing is lost by dropping its copies.
 *
 * Matching is case-insensitive and ignores surrounding space, and a wording
 * that **starts** with what has been typed always beats one that merely
 * contains it. Typing "поч" should offer "Починить колесо" before "Не забыть
 * починить колесо", even if the second was written more often: the user is
 * typing from the beginning, so the beginning is what they mean.
 *
 * Within each of those two groups: used more often first, then used more
 * recently, then alphabetically. The rule is [suggestionOrder], shared with
 * the food suggestions.
 *
 * A task with no date has no [TaskSuggestion.lastUsedOn]; it sorts as older
 * than anything dated, which is what "Someday" means.
 */
fun taskSuggestionsFor(
    typed: String,
    tasks: List<Task>,
    subtasks: List<Subtask>,
    limit: Int = 5,
): List<TaskSuggestion> {
    val needle = suggestionNeedle(typed) ?: return emptyList()

    val pool = tasks.filter { it.originTaskId == null && it.description.isNotBlank() }

    val matching = pool.filter { needle in it.description.trim().lowercase() }
    if (matching.isEmpty()) return emptyList()

    val subtasksByTask = subtasks.groupBy { it.taskId }

    return matching
        .groupBy { it.description.trim() }
        .map { (description, group) ->
            // The most recent writing of it. Undated tasks lose to dated ones,
            // and among equals the one created last wins — ids only go up.
            val newest = group.maxWithOrNull(
                compareBy<Task>({ it.date ?: LocalDate.MIN }, { it.id })
            )!!

            TaskSuggestion(
                description = description,
                subtaskDescriptions = subtasksByTask[newest.id]
                    .orEmpty()
                    .filter { it.originSubtaskId == null }
                    .sortedBy { it.order }
                    .map { it.description }
                    .filter { it.isNotBlank() },
                timesUsed = group.size,
                lastUsedOn = group.mapNotNull { it.date }.maxOrNull(),
            )
        }
        .sortedWith(
            suggestionOrder(
                needle,
                text = { it.description },
                timesUsed = { it.timesUsed },
                lastUsedOn = { it.lastUsedOn },
            )
        )
        .take(limit)
}
