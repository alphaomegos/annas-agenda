# Anna’s Agenda

A minimal calendar + to-do app for my dear friend Anna. Calendar has tasks with subtasks, recurring rules, and day/month navigation.

## Features
- **Calendar view**: month screen + day screen
- **Tasks & subtasks**: create, reorder, mark done
- **Recurring**:
  - recurring **tasks**
  - recurring **subtasks** (shown on the Recurring screen as a task with that single subtask)
- **Copy to date**:
  - copy a **task** → copies the task + all its subtasks
  - copy a **subtask** → copies the parent task + only that subtask
- **Backup**: export/import JSON
- **Languages**: EN / RU / SR (Latin), runtime switch

## Tech stack
- Kotlin
- Jetpack Compose + Navigation
- DataStore (JSON persistence)

## How to run
1. Open the project in Android Studio
2. Sync Gradle
3. Run on an emulator or a real device (USB debugging)
