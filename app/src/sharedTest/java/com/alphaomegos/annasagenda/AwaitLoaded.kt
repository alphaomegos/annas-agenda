package com.alphaomegos.annasagenda

/** Real milliseconds per look. See [awaitLoaded] for why this is not a delay. */
private const val WAIT_STEP_MILLIS = 20L

/** 1000 × 20ms = twenty real seconds, against a load that takes about fifty. */
private const val WAIT_STEPS = 1000

/**
 * Waits until the view model has read the saved state.
 *
 * **`Thread.sleep`, not `delay`, and that is the whole point of this file.**
 *
 * Under Robolectric the looper's clock is virtual: it jumps forward whenever
 * the scheduler runs out of work, so `delay(20)` returns after twenty
 * milliseconds *of that clock* and no time at all of the kind a wristwatch
 * measures. `System.currentTimeMillis()` and `System.nanoTime()` are shadowed
 * onto the same virtual clock, so measuring the wait with either of them
 * measures the same fiction.
 *
 * Meanwhile the thing being waited for — DataStore reading a file — happens on
 * a real background thread in real time. So a delay-based wait of "two
 * seconds", or of "thirty seconds", both finish in well under a millisecond of
 * real time, and whether the load beat them to it depends on how busy the
 * machine happens to be. That is exactly what was seen: green on its own,
 * red under `clean test assembleRelease`, a different test each run, and
 * completely unmoved by making the virtual deadline fifteen times longer.
 *
 * `Thread.sleep` is not shadowed. It sleeps. Twenty real seconds is four
 * hundred times the load's usual fifty milliseconds, so reaching the end of
 * this loop means something is genuinely stuck rather than slow.
 *
 * Sleeping the test thread is safe here, and worth saying why: these tests put
 * `Dispatchers.Unconfined` in Main's place, so the load's last step runs on
 * whichever thread finishes the read rather than needing this one. On a real
 * device the main looper is a different thread again.
 *
 * The count is a count rather than a clock for the same reason as everything
 * above — there is no clock here worth reading.
 */
internal suspend fun awaitLoaded(vm: AppViewModel) {
    if (vm.isLoaded.value) return

    repeat(WAIT_STEPS) {
        @Suppress("BlockingMethodInNonBlockingContext")
        Thread.sleep(WAIT_STEP_MILLIS)

        if (vm.isLoaded.value) return
    }

    // Says what the load ended up as, so that the next time this ever fires it
    // is a diagnosis rather than the start of one.
    error(
        "AppViewModel did not finish loading within " +
            "${WAIT_STEPS * WAIT_STEP_MILLIS}ms of real time. " +
            "storageFailure=${vm.storageFailure.value}"
    )
}

/**
 * The same wait, plus the thing every one of these tests assumes and none of
 * them said: that the store actually worked.
 *
 * A view model that loads into a storage failure is loaded — isLoaded is true,
 * the wait returns — and then every assertion after it is about an app holding
 * its defaults with autosave switched off. Those assertions fail one at a
 * time, in whatever way the test happens to be written, and none of them
 * mentions the store. Naming it here turns all of that into one sentence.
 */
internal suspend fun awaitWorkingStore(vm: AppViewModel) {
    awaitLoaded(vm)

    val failure = vm.storageFailure.value
    check(failure == null) { "AppViewModel loaded into a storage failure: $failure" }
}
