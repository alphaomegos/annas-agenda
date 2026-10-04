package com.alphaomegos.annasagenda.model

/**
 * A metro map the user writes down, for one city (04.10).
 *
 * The map holds no pictures and no coordinates that matter yet: lines,
 * stations, the transfers between them, the exits, and where in the train to
 * sit for a transfer or an exit. Routes are worked out from it each time
 * (support/MetroRouting.kt); nothing about a route is stored.
 *
 * **Ids inside a scheme are the scheme's own.** A station's id is unique
 * within its scheme, not across the app, so a scheme moves as one piece:
 * out of a backup into the shipped library, and out of the library into the
 * user's own copy, without renumbering anything.
 *
 * [librarySource] names the library scheme this one was copied from, or is
 * null for a scheme the user started from nothing. A library scheme is never
 * edited in place: editing it makes a copy (decided 04.10), so a newer
 * library in a later build cannot overwrite what the user changed.
 */
data class MetroScheme(
    val id: Long,
    val city: String,
    val lines: List<MetroLine> = emptyList(),
    val stations: List<MetroStation> = emptyList(),
    val transfers: List<MetroTransfer> = emptyList(),
    val exits: List<MetroExit> = emptyList(),
    val hints: List<MetroHint> = emptyList(),
    val segmentTimes: List<MetroSegmentTime> = emptyList(),
    val defaultSegmentMinutes: Int = METRO_DEFAULT_SEGMENT_MINUTES,
    val defaultTransferMinutes: Int = METRO_DEFAULT_TRANSFER_MINUTES,
    val librarySource: String? = null,
)

/** What a ride between two neighbouring stations costs when the user has not said (04.10). */
const val METRO_DEFAULT_SEGMENT_MINUTES = 3

/** What a transfer costs when the user has not said (04.10). */
const val METRO_DEFAULT_TRANSFER_MINUTES = 3

/**
 * One line: its number as people say it ("11", "8А"), its name, its colour,
 * and the order of its stations.
 *
 * [trunk] is the line's main run, in order. A [ring] line's trunk closes on
 * itself: the last station is next to the first.
 *
 * [branches] are the forks. A branch leaves from a station already on the
 * line and runs outwards. Trains run **from the trunk into each branch**
 * (decided 04.10): from a branch the train goes on along the line towards
 * the start of [trunk] — or towards its end when the branch leaves from the
 * very first station. Going from one branch into another, or from a branch
 * on into the rest of the trunk, means changing trains at the fork. So the
 * trunk is written from the end every branch's trains go to.
 *
 * [carCount] and [doorsPerCar] are what a hint's car and door are counted
 * against: they let a hint say "the last car" and let a future picture draw
 * the train.
 *
 * [color] is ARGB, as Compose's Color(Long) takes it. A station is drawn in
 * the colour of its line.
 */
data class MetroLine(
    val id: Long,
    val label: String,
    val name: String,
    val color: Long,
    val ring: Boolean = false,
    val carCount: Int = 8,
    val doorsPerCar: Int = 4,
    val trunk: List<Long> = emptyList(),
    val branches: List<MetroBranch> = emptyList(),
)

/**
 * A fork: [stationIds] in order going away from [fromStationId], which is
 * already on the line — on the trunk or on another branch.
 */
data class MetroBranch(
    val id: Long,
    val fromStationId: Long,
    val stationIds: List<Long>,
)

/**
 * A station. It belongs to one line; a station shared by two lines is two
 * stations joined by a [MetroTransfer], as passengers see it.
 *
 * [mapX] and [mapY] are kept empty for the picture of the map that is to come
 * (agreed 04.10): it will draw from this data, and positions the user moves
 * will need somewhere to live.
 */
data class MetroStation(
    val id: Long,
    val lineId: Long,
    val name: String,
    val mapX: Float? = null,
    val mapY: Float? = null,
)

/** A walk between two stations, either way. [minutes] null means the scheme's default. */
data class MetroTransfer(
    val id: Long,
    val aStationId: Long,
    val bStationId: Long,
    val minutes: Int? = null,
)

/** How long the train takes between two neighbouring stations, either way. */
data class MetroSegmentTime(
    val aStationId: Long,
    val bStationId: Long,
    val minutes: Int,
)

/** A way out of a station, named for where it leads ("к МЦК", "к дому"). */
data class MetroExit(
    val id: Long,
    val stationId: Long,
    val name: String,
)

/**
 * Where to sit: [car] and [door], both counted from 1 from the head of the
 * train in the direction it goes (decided 04.10).
 *
 * The hint belongs to the station where one gets **off**, [stationId], and to
 * the side the train comes from, [fromStationId] — "coming from Чистые
 * пруды", as one says it. That names the direction without doubt on a
 * straight line, on a ring and at a fork alike.
 *
 * It says what the place is good for: a transfer to a station of another line,
 * or one of this station's exits.
 */
data class MetroHint(
    val id: Long,
    val stationId: Long,
    val fromStationId: Long,
    val car: Int,
    val door: Int,
    val target: MetroHintTarget,
)

sealed interface MetroHintTarget {
    data class Transfer(val toStationId: Long) : MetroHintTarget
    data class Exit(val exitId: Long) : MetroHintTarget
}
