package com.alphaomegos.annasagenda

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = AppStateStore(app.applicationContext)

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = store.load()
            _state.value = loaded

            val maxId = (loaded.tasks.map { it.id } + loaded.subtasks.map { it.id }).maxOrNull() ?: 0L
            nextId = maxId + 1

            _isLoaded.value = true

            state
                .drop(1)       // skip the initially loaded state
                .debounce(400) // reduce disk writes while user edits quickly
                .collect { store.save(it) }
        }
    }

    fun resetAllData() {
        val empty = AppState()
        _state.value = empty
        nextId = 1L
        viewModelScope.launch {
            store.save(empty)
        }
    }

    fun exportBackupJson(): String {
        return store.encodeToJson(_state.value)
    }

    fun importBackupJson(raw: String): Boolean {
        val decoded = store.decodeFromJson(raw) ?: return false

        _state.value = decoded

        val maxId = (decoded.tasks.map { it.id } + decoded.subtasks.map { it.id }).maxOrNull() ?: 0L
        nextId = maxId + 1L

        viewModelScope.launch {
            store.save(decoded)
        }
        return true
    }


    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var nextId: Long = 1L
    private fun newId(): Long = nextId++



    private fun nextTaskOrderForDate(date: LocalDate?): Int =
        (_state.value.tasks.filter { it.date == date }.maxOfOrNull { it.order } ?: -1) + 1



    private fun nextSubtaskOrderFor(taskId: Long): Int =
        (_state.value.subtasks.filter { it.taskId == taskId }.maxOfOrNull { it.order } ?: -1) + 1

    private fun suppress(key: String) {
        val st = _state.value
        _state.value = st.copy(suppressedRecurrences = st.suppressedRecurrences + key)
    }

    private fun refreshHasSubtasks() {
        val idsWithSubs = _state.value.subtasks.map { it.taskId }.toSet()
        val updatedTasks = _state.value.tasks.map { t ->
            t.copy(hasSubtasks = idsWithSubs.contains(t.id))
        }
        _state.value = _state.value.copy(tasks = updatedTasks)
    }

    private fun refreshHasSubtasksForTask(taskId: Long, tasks: MutableList<Task>, subs: List<Subtask>) {
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx >= 0) {
            val has = subs.any { it.taskId == taskId }
            tasks[idx] = tasks[idx].copy(hasSubtasks = has)
        }
    }


    /* ---------------------------
       Recurrence (generated instances)
    ---------------------------- */

    private fun matchesRepeat(anchor: LocalDate, date: LocalDate, rule: RepeatRule): Boolean {
        if (!date.isAfter(anchor)) return false
        val interval = rule.interval.coerceAtLeast(1)

        return when (rule.freq) {
            RepeatFreq.DAILY -> {
                val days = ChronoUnit.DAYS.between(anchor, date)
                days % interval == 0L
            }
            RepeatFreq.WEEKLY -> {
                if (rule.weekDays.isNotEmpty() && date.dayOfWeek !in rule.weekDays) return false
                val wf = WeekFields.of(Locale.getDefault())
                val a = anchor.with(wf.dayOfWeek(), 1)
                val d = date.with(wf.dayOfWeek(), 1)
                val weeks = ChronoUnit.WEEKS.between(a, d)
                weeks % interval == 0L
            }
            RepeatFreq.MONTHLY -> {
                val dom = rule.dayOfMonth ?: anchor.dayOfMonth
                if (date.dayOfMonth != dom) return false
                val months = ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), date.withDayOfMonth(1))
                months % interval == 0L
            }
        }
    }

    fun ensureGeneratedInRange(start: LocalDate, end: LocalDate) {
        val cur = _state.value

        val newTasks = cur.tasks.toMutableList()
        val newSubtasks = cur.subtasks.toMutableList()
        val subtasksByTask = cur.subtasks.groupBy { it.taskId }

        fun isSuppressed(key: String) = cur.suppressedRecurrences.contains(key)

        fun nextTaskOrderIn(date: LocalDate): Int =
            (newTasks.filter { it.date == date }.maxOfOrNull { it.order } ?: -1) + 1

        fun cloneSubtaskIntoTask(templateSub: Subtask, newTaskId: Long): Subtask {
            val s = templateSub.copy(
                id = newId(),
                taskId = newTaskId,
                isDone = false,
                repeatRule = null,
                originSubtaskId = templateSub.id
            )
            newSubtasks.add(s)
            return s
        }

        fun findGeneratedTask(originTaskId: Long, targetDate: LocalDate): Task? {
            return newTasks.firstOrNull { it.originTaskId == originTaskId && it.date == targetDate }
        }

        fun cloneTaskForDate(templateTask: Task, targetDate: LocalDate): Task {
            val t = templateTask.copy(
                id = newId(),
                order = nextTaskOrderIn(targetDate),
                date = targetDate,
                isDone = false,
                repeatRule = null,
                originTaskId = templateTask.id
            )
            newTasks.add(t)
            return t
        }

        // Templates are tasks that live on some date and are not generated instances.
        val dateTemplates = cur.tasks.filter { it.originTaskId == null && it.date != null }

        for (t in dateTemplates) {
            val anchor = t.date ?: continue
            val subs = subtasksByTask[t.id].orEmpty().filter { it.originSubtaskId == null }

            // 1) repeating task -> clone task + all its subtasks
            val taskRule = t.repeatRule
            if (taskRule != null) {
                var d = start
                while (!d.isAfter(end)) {
                    val epoch = d.toEpochDay()
                    if (matchesRepeat(anchor, d, taskRule)) {
                        val suppressKey = "T:${t.id}:$epoch"
                        if (!isSuppressed(suppressKey)) {
                            val existing = findGeneratedTask(t.id, d)
                            if (existing == null) {
                                val createdTask = cloneTaskForDate(t, d)
                                for (srcSub in subs) {
                                    val suppressSubKey = "S:${srcSub.id}:$epoch"
                                    if (!isSuppressed(suppressSubKey)) {
                                        cloneSubtaskIntoTask(srcSub, createdTask.id)
                                    }
                                }
                                refreshHasSubtasksForTask(createdTask.id, newTasks, newSubtasks)
                            }
                        }
                    }
                    d = d.plusDays(1)
                }
            }

            // 2) repeating subtasks -> ensure task clone exists and add only that subtask
            for (s in subs) {
                val rule = s.repeatRule ?: continue
                var d = start
                while (!d.isAfter(end)) {
                    val epoch = d.toEpochDay()
                    if (matchesRepeat(anchor, d, rule)) {
                        val suppressKey = "S:${s.id}:$epoch"
                        if (!isSuppressed(suppressKey)) {
                            val taskForSub = findGeneratedTask(t.id, d) ?: cloneTaskForDate(t, d)
                            val alreadySub = newSubtasks.any { it.taskId == taskForSub.id && it.originSubtaskId == s.id }
                            if (!alreadySub) {
                                cloneSubtaskIntoTask(s, taskForSub.id)
                                refreshHasSubtasksForTask(taskForSub.id, newTasks, newSubtasks)
                            }
                        }
                    }
                    d = d.plusDays(1)
                }
            }
        }

        _state.value = cur.copy(tasks = newTasks, subtasks = newSubtasks)
    }


    fun setTaskRepeatRule(taskId: Long, rule: RepeatRule?) {
        val st = _state.value
        val updated = st.tasks.map { t ->
            if (t.id == taskId) t.copy(repeatRule = rule) else t
        }
        _state.value = st.copy(tasks = updated)
    }

    fun setSubtaskRepeatRule(subtaskId: Long, rule: RepeatRule?) {
        val st = _state.value
        val updated = st.subtasks.map { s ->
            if (s.id == subtaskId) s.copy(repeatRule = rule) else s
        }
        _state.value = st.copy(subtasks = updated)
        refreshHasSubtasks()
    }

    /* ---------------------------
       Tasks
    ---------------------------- */

    fun createTaskForDate(
        date: LocalDate?,
        time: LocalTime?,
        description: String,
        colorArgb: Long? = null,
        hasSubtasks: Boolean = false,
        repeatRule: RepeatRule? = null,
    ): Long {
        val id = newId()
        val task = Task(
            id = id,
            order = nextTaskOrderForDate(date),
            date = date,
            time = time,
            description = description,
            colorArgb = colorArgb,
            hasSubtasks = hasSubtasks,
            repeatRule = repeatRule,
        )
        _state.value = _state.value.copy(tasks = _state.value.tasks + task)
        return id
    }


    fun updateTaskDescription(taskId: Long, description: String) {
        val clean = description.trim()
        if (clean.isBlank()) return
        val updated = _state.value.tasks.map { t ->
            if (t.id == taskId) t.copy(description = clean) else t
        }
        _state.value = _state.value.copy(tasks = updated)
    }

    fun deleteTask(taskId: Long) {
        val cur = _state.value
        val victim = cur.tasks.firstOrNull { it.id == taskId } ?: return

        // 1) If deleting a generated instance -> suppress regeneration for that date and really delete it.
        if (victim.originTaskId != null && victim.date != null) {
            val key = "T:${victim.originTaskId}:${victim.date.toEpochDay()}"
            val newTasks = cur.tasks.filterNot { it.id == taskId }
            val newSubs = cur.subtasks.filterNot { it.taskId == taskId }

            _state.value = cur.copy(
                suppressedRecurrences = cur.suppressedRecurrences + key,
                tasks = newTasks,
                subtasks = newSubs
            )
            refreshHasSubtasks()
            return
        }


        // 2) If deleting a template recurring task (originTaskId == null && repeatRule != null)
        // -> treat as "delete only this day": keep template, just hide it for its own date.
        if (victim.originTaskId == null && victim.repeatRule != null && victim.date != null) {
            suppress("T:${victim.id}:${victim.date.toEpochDay()}")
            // IMPORTANT: do not remove template task/subtasks, they define the series.
            return
        }

        // 3) Normal delete
        val newTasks = cur.tasks.filterNot { it.id == taskId }
        val newSubs = cur.subtasks.filterNot { it.taskId == taskId }
        _state.value = cur.copy(tasks = newTasks, subtasks = newSubs)
        refreshHasSubtasks()
    }

    fun deleteTaskSeriesFrom(templateTaskId: Long, fromDate: LocalDate = LocalDate.now()) {
        val cur = _state.value
        val template = cur.tasks.firstOrNull { it.id == templateTaskId && it.originTaskId == null } ?: return

        val idsToDelete = mutableSetOf<Long>()

        // Delete the template itself only if it is on/after fromDate (i.e. "starting today").
        val td = template.date
        if (td == null || !td.isBefore(fromDate)) {
            idsToDelete.add(template.id)
        }

        // Delete already generated instances on/after fromDate.
        cur.tasks.filter { it.originTaskId == templateTaskId }.forEach { inst ->
            val d = inst.date
            if (d == null || !d.isBefore(fromDate)) {
                idsToDelete.add(inst.id)
            }
        }

        val newTasks = cur.tasks
            .filterNot { it.id in idsToDelete }
            // If template is in the past, keep it as history but stop repeating.
            .map { t -> if (t.id == templateTaskId) t.copy(repeatRule = null) else t }

        val newSubs = cur.subtasks.filterNot { it.taskId in idsToDelete }

        _state.value = cur.copy(tasks = newTasks, subtasks = newSubs)
        refreshHasSubtasks()
    }

    fun deleteSubtaskSeriesFrom(templateSubtaskId: Long, fromDate: LocalDate = LocalDate.now()) {
        val cur = _state.value
        val templateSub = cur.subtasks.firstOrNull { it.id == templateSubtaskId && it.originSubtaskId == null } ?: return
        val parentTemplateTask = cur.tasks.firstOrNull { it.id == templateSub.taskId && it.originTaskId == null } ?: return

        val tasksById = cur.tasks.associateBy { it.id }

        val subIdsToDelete = cur.subtasks
            .filter { it.originSubtaskId == templateSubtaskId }
            .filter { inst ->
                val d = tasksById[inst.taskId]?.date
                d == null || !d.isBefore(fromDate)
            }
            .mapTo(mutableSetOf()) { it.id }

        val newSubs = cur.subtasks
            .filterNot { it.id in subIdsToDelete }
            .map { s -> if (s.id == templateSubtaskId) s.copy(repeatRule = null) else s }

        // If the parent task itself is NOT repeating, we can delete empty generated task instances
        // that existed only to host this repeating subtask.
        val parentIsRepeating = parentTemplateTask.repeatRule != null
        if (!parentIsRepeating) {
            val remainingByTask = newSubs.groupBy { it.taskId }

            val emptyGeneratedTaskIds = cur.tasks
                .filter { it.originTaskId == parentTemplateTask.id }
                .filter { inst ->
                    val d = inst.date
                    (d == null || !d.isBefore(fromDate)) && remainingByTask[inst.id].isNullOrEmpty()
                }
                .map { it.id }
                .toSet()

            val newTasks = if (emptyGeneratedTaskIds.isEmpty()) cur.tasks else cur.tasks.filterNot { it.id in emptyGeneratedTaskIds }
            val newSubs2 = if (emptyGeneratedTaskIds.isEmpty()) newSubs else newSubs.filterNot { it.taskId in emptyGeneratedTaskIds }

            _state.value = cur.copy(tasks = newTasks, subtasks = newSubs2)
        } else {
            _state.value = cur.copy(subtasks = newSubs)
        }

        refreshHasSubtasks()
    }


    fun rescheduleTaskToDate(taskId: Long, newDate: LocalDate?) {
        val cur = _state.value
        val victim = cur.tasks.firstOrNull { it.id == taskId } ?: return

        val newOrder = if (victim.date == newDate) victim.order else nextTaskOrderForDate(newDate)

        val updated = cur.tasks.map { t ->
            if (t.id == taskId) t.copy(date = newDate, order = newOrder) else t
        }
        _state.value = cur.copy(tasks = updated)
    }

    fun copyTaskToDate(taskId: Long, targetDate: LocalDate) {
        val cur = _state.value
        val srcTask = cur.tasks.firstOrNull { it.id == taskId } ?: return

        val srcSubs = cur.subtasks
            .filter { it.taskId == taskId }
            .sortedWith(compareBy<Subtask>({ it.order }, { it.id }))

        val newTaskId = createTaskForDate(
            date = targetDate,
            time = srcTask.time,
            description = srcTask.description,
            colorArgb = srcTask.colorArgb,
            hasSubtasks = srcSubs.isNotEmpty(),
            repeatRule = null
        )

        for (s in srcSubs) {
            createSubtask(
                taskId = newTaskId,
                description = s.description,
                colorArgb = s.colorArgb
            )
        }
    }


    /* ---------------------------
       Subtasks
    ---------------------------- */

    fun createSubtask(taskId: Long, description: String, colorArgb: Long? = null): Long {
        val id = newId()
        val subtask = Subtask(
            id = id,
            order = nextSubtaskOrderFor(taskId),
            taskId = taskId,
            description = description.trim(),
            colorArgb = colorArgb,
            isDone = false
        )
        _state.value = _state.value.copy(subtasks = _state.value.subtasks + subtask)
        refreshHasSubtasks()

        val parent = _state.value.tasks.firstOrNull { it.id == taskId }
        if (parent?.isDone == true) toggleSubtaskDone(id)

        return id
    }


    fun updateSubtaskDescription(subtaskId: Long, description: String) {
        val clean = description.trim()
        if (clean.isBlank()) return
        val updated = _state.value.subtasks.map { s ->
            if (s.id == subtaskId) s.copy(description = clean) else s
        }
        _state.value = _state.value.copy(subtasks = updated)
    }

    fun deleteSubtask(subtaskId: Long) {
        val cur = _state.value
        val victim = cur.subtasks.firstOrNull { it.id == subtaskId }

        // If deleting a generated instance, suppress it for that date.
        if (victim?.originSubtaskId != null) {
            val parentTask = cur.tasks.firstOrNull { it.id == victim.taskId }
            val epoch = parentTask?.date?.toEpochDay()
            if (epoch != null) {
                suppress("S:${victim.originSubtaskId}:$epoch")
            }
        }

        val newSubs = cur.subtasks.filterNot { it.id == subtaskId }
        _state.value = cur.copy(subtasks = newSubs)
        refreshHasSubtasks()

        // Recompute parent task done state after deletion, but only if it still has subtasks.
        val taskId = victim?.taskId ?: return
        val remaining = newSubs.filter { it.taskId == taskId }
        if (remaining.isNotEmpty()) {
            val allDone = remaining.all { it.isDone }
            val newTasks = _state.value.tasks.map { t ->
                if (t.id == taskId) t.copy(isDone = allDone) else t
            }
            _state.value = _state.value.copy(tasks = newTasks)
        }
    }

    fun copySubtaskToDate(subtaskId: Long, targetDate: LocalDate) {
        val cur = _state.value
        val srcSub = cur.subtasks.firstOrNull { it.id == subtaskId } ?: return
        val parent = cur.tasks.firstOrNull { it.id == srcSub.taskId } ?: return

        val newTaskId = createTaskForDate(
            date = targetDate,
            time = parent.time,
            description = parent.description,
            colorArgb = parent.colorArgb,
            hasSubtasks = true,
            repeatRule = null
        )

        createSubtask(
            taskId = newTaskId,
            description = srcSub.description,
            colorArgb = srcSub.colorArgb
        )
    }

    fun moveSubtask(subtaskId: Long, targetTaskId: Long) {
        val cur = _state.value
        val victim = cur.subtasks.firstOrNull { it.id == subtaskId } ?: return

        val newOrder = if (victim.taskId == targetTaskId) victim.order else nextSubtaskOrderFor(targetTaskId)

        val updated = cur.subtasks.map { s ->
            if (s.id == subtaskId) s.copy(taskId = targetTaskId, order = newOrder) else s
        }
        _state.value = cur.copy(subtasks = updated)
        refreshHasSubtasks()
        recomputeTaskDoneFromSubtasks()
    }


    fun moveTaskUp(taskId: Long) {
        val cur = _state.value
        val victim = cur.tasks.firstOrNull { it.id == taskId } ?: return
        val date = victim.date

        val siblings = cur.tasks
            .filter { it.date == date }
            .sortedWith(compareBy<Task>({ it.order }, { it.id }))

        val idx = siblings.indexOfFirst { it.id == taskId }
        if (idx <= 0) return

        val reordered = siblings.toMutableList()
        val tmp = reordered[idx - 1]
        reordered[idx - 1] = reordered[idx]
        reordered[idx] = tmp

        val idToOrder = reordered.mapIndexed { i, t -> t.id to i }.toMap()
        val newTasks = cur.tasks.map { t -> idToOrder[t.id]?.let { t.copy(order = it) } ?: t }
        _state.value = cur.copy(tasks = newTasks)
    }

    fun moveTaskDown(taskId: Long) {
        val cur = _state.value
        val victim = cur.tasks.firstOrNull { it.id == taskId } ?: return
        val date = victim.date

        val siblings = cur.tasks
            .filter { it.date == date }
            .sortedWith(compareBy<Task>({ it.order }, { it.id }))

        val idx = siblings.indexOfFirst { it.id == taskId }
        if (idx < 0 || idx >= siblings.lastIndex) return

        val reordered = siblings.toMutableList()
        val tmp = reordered[idx + 1]
        reordered[idx + 1] = reordered[idx]
        reordered[idx] = tmp

        val idToOrder = reordered.mapIndexed { i, t -> t.id to i }.toMap()
        val newTasks = cur.tasks.map { t -> idToOrder[t.id]?.let { t.copy(order = it) } ?: t }
        _state.value = cur.copy(tasks = newTasks)
    }


    fun moveSubtaskUp(subtaskId: Long) {
        val cur = _state.value
        val victim = cur.subtasks.firstOrNull { it.id == subtaskId } ?: return
        val taskId = victim.taskId

        val siblings = cur.subtasks
            .filter { it.taskId == taskId }
            .sortedWith(compareBy<Subtask>({ it.order }, { it.id }))

        val idx = siblings.indexOfFirst { it.id == subtaskId }
        if (idx <= 0) return

        val reordered = siblings.toMutableList()
        val tmp = reordered[idx - 1]
        reordered[idx - 1] = reordered[idx]
        reordered[idx] = tmp

        val idToOrder = reordered.mapIndexed { i, s -> s.id to i }.toMap()
        val newSubs = cur.subtasks.map { s -> idToOrder[s.id]?.let { s.copy(order = it) } ?: s }
        _state.value = cur.copy(subtasks = newSubs)
        refreshHasSubtasks()
        recomputeTaskDoneFromSubtasks()
    }

    fun moveSubtaskDown(subtaskId: Long) {
        val cur = _state.value
        val victim = cur.subtasks.firstOrNull { it.id == subtaskId } ?: return
        val taskId = victim.taskId

        val siblings = cur.subtasks
            .filter { it.taskId == taskId }
            .sortedWith(compareBy<Subtask>({ it.order }, { it.id }))

        val idx = siblings.indexOfFirst { it.id == subtaskId }
        if (idx < 0 || idx >= siblings.lastIndex) return

        val reordered = siblings.toMutableList()
        val tmp = reordered[idx + 1]
        reordered[idx + 1] = reordered[idx]
        reordered[idx] = tmp

        val idToOrder = reordered.mapIndexed { i, s -> s.id to i }.toMap()
        val newSubs = cur.subtasks.map { s -> idToOrder[s.id]?.let { s.copy(order = it) } ?: s }
        _state.value = cur.copy(subtasks = newSubs)
        refreshHasSubtasks()
        recomputeTaskDoneFromSubtasks()
    }



    /* ---------------------------
       Done flags sync (Task <-> Subtasks)
    ---------------------------- */

    fun toggleTaskDone(taskId: Long) {
        val cur = _state.value
        val task = cur.tasks.firstOrNull { it.id == taskId } ?: return
        val newDone = !task.isDone

        val newTasks = cur.tasks.map { t ->
            if (t.id == taskId) t.copy(isDone = newDone) else t
        }

        val hasSubs = cur.subtasks.any { it.taskId == taskId }
        val newSubs = if (!hasSubs) {
            cur.subtasks
        } else {
            cur.subtasks.map { s ->
                if (s.taskId == taskId) s.copy(isDone = newDone) else s
            }
        }

        _state.value = cur.copy(tasks = newTasks, subtasks = newSubs)
    }

    fun toggleSubtaskDone(subtaskId: Long) {
        val cur = _state.value
        val st = cur.subtasks.firstOrNull { it.id == subtaskId } ?: return

        val newSubs = cur.subtasks.map { s ->
            if (s.id == subtaskId) s.copy(isDone = !s.isDone) else s
        }

        val taskId = st.taskId
        val related = newSubs.filter { it.taskId == taskId }
        val allDone = related.isNotEmpty() && related.all { it.isDone }

        val newTasks = cur.tasks.map { t ->
            if (t.id == taskId) t.copy(isDone = allDone) else t
        }

        _state.value = cur.copy(tasks = newTasks, subtasks = newSubs)
    }

    private fun recomputeTaskDoneFromSubtasks() {
        val cur = _state.value
        val subsByTask = cur.subtasks.groupBy { it.taskId }
        val newTasks = cur.tasks.map { t ->
            val subs = subsByTask[t.id].orEmpty()
            if (subs.isEmpty()) t
            else t.copy(isDone = subs.all { it.isDone })
        }
        _state.value = cur.copy(tasks = newTasks)
    }

    /* ---------------------------
       Colors
    ---------------------------- */

    fun setTaskColor(taskId: Long, colorArgb: Long?) {
        val updated = _state.value.tasks.map { t ->
            if (t.id == taskId) t.copy(colorArgb = colorArgb) else t
        }
        _state.value = _state.value.copy(tasks = updated)
    }

    fun setSubtaskColor(subtaskId: Long, colorArgb: Long?) {
        val updated = _state.value.subtasks.map { s ->
            if (s.id == subtaskId) s.copy(colorArgb = colorArgb) else s
        }
        _state.value = _state.value.copy(subtasks = updated)
    }
}