package com.alphaomegos.annasagenda.data

import com.alphaomegos.annasagenda.model.SidecarBackup
import com.alphaomegos.annasagenda.model.SidecarBackupLimits
import com.alphaomegos.annasagenda.model.SidecarBackupListing
import com.alphaomegos.annasagenda.model.SidecarBackupSlot
import com.alphaomegos.annasagenda.model.SidecarBackupWhenFull
import kotlinx.serialization.Serializable

/*
 * What oursidecar's server answers about backups (16.10), as its
 * claude/backups-api.md writes it: camelCase, times in unix seconds,
 * errors as { error, code? } with what the code needs beside it. Read
 * leniently (sidecarJson): a field added later is let past.
 */

@Serializable
internal data class SidecarBackupDeviceDto(
    val id: String,
    val name: String = "",
)

/** The answer to a phone signing in for backups. */
@Serializable
internal data class SidecarBackupSignInReplyDto(
    val token: String? = null,
    val device: SidecarBackupDeviceDto? = null,
)

@Serializable
internal data class SidecarBackupDto(
    val id: String,
    val slot: String = "",
    val state: String = "",
    val size: Long = 0L,
    val sha256: String = "",
    val encrypted: Boolean = false,
    val deviceName: String? = null,
    val appVersion: String? = null,
    val note: String? = null,
    val createdAt: Long = 0L,
    val completedAt: Long? = null,
)

@Serializable
internal data class SidecarBackupSettingsDto(
    val whenFull: String = "ask",
)

@Serializable
internal data class SidecarBackupLimitsDto(
    val manual: Int = 3,
    val auto: Int = 1,
    val maxSize: Long = 200L * 1024 * 1024,
    val partSize: Long = 8L * 1024 * 1024,
)

/** The answer to GET /api/backups. */
@Serializable
internal data class SidecarBackupListReplyDto(
    val backups: List<SidecarBackupDto> = emptyList(),
    val settings: SidecarBackupSettingsDto = SidecarBackupSettingsDto(),
    val limits: SidecarBackupLimitsDto = SidecarBackupLimitsDto(),
)

@Serializable
internal data class SidecarBackupUploadDto(
    val partSize: Long = 0L,
    val partCount: Int = 0,
    val received: List<Int> = emptyList(),
)

/** The answer to starting a backup, and to asking after one (GET /api/backups/{id}). */
@Serializable
internal data class SidecarBackupUploadReplyDto(
    val backup: SidecarBackupDto? = null,
    val replaces: String? = null,
    val upload: SidecarBackupUploadDto? = null,
)

/** An error, and what its code brings with it: the backups to choose from, the parts missing. */
@Serializable
internal data class SidecarBackupErrorDto(
    val error: String? = null,
    val code: String? = null,
    val backups: List<SidecarBackupDto> = emptyList(),
    val missing: List<Int> = emptyList(),
)

/** The body of starting a backup; what is null is not sent. */
@Serializable
internal data class SidecarBackupStartDto(
    val slot: String,
    val size: Long,
    val sha256: String,
    val encrypted: Boolean,
    val appVersion: String? = null,
    val note: String? = null,
    val replace: String? = null,
)

@Serializable
internal data class SidecarBackupSignInDto(
    val email: String,
    val password: String,
    val deviceName: String,
)

@Serializable
internal data class SidecarBackupWhenFullDto(
    val whenFull: String,
)

/** A backup as the app knows it; null for a kind of place it does not know. */
internal fun SidecarBackupDto.toDomain(): SidecarBackup? {
    val kind = SidecarBackupSlot.entries.firstOrNull { it.wire == slot } ?: return null
    return SidecarBackup(
        id = id,
        slot = kind,
        ready = state == "ready",
        size = size,
        sha256 = sha256.lowercase(),
        encrypted = encrypted,
        deviceName = deviceName,
        appVersion = appVersion,
        note = note,
        createdAtSeconds = createdAt,
        completedAtSeconds = completedAt,
    )
}

internal fun sidecarBackupWhenFullOf(wire: String?): SidecarBackupWhenFull =
    SidecarBackupWhenFull.entries.firstOrNull { it.wire == wire } ?: SidecarBackupWhenFull.ASK

internal fun SidecarBackupListReplyDto.toDomain(): SidecarBackupListing = SidecarBackupListing(
    backups = backups.mapNotNull { it.toDomain() },
    whenFull = sidecarBackupWhenFullOf(settings.whenFull),
    limits = SidecarBackupLimits(
        manual = limits.manual,
        auto = limits.auto,
        maxSize = limits.maxSize,
        partSize = limits.partSize,
    ),
)

/**
 * What this phone keeps of its backups in oursidecar (16.10), in the
 * no-backup folder: the server and the account, the phone's token and the
 * encryption key sealed by the keystore, and the choices made here.
 */
@Serializable
internal data class SidecarBackupAccountDto(
    val server: String? = null,
    val email: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val sealedToken: String? = null,
    val needsSignIn: Boolean = false,
    val notEnabled: Boolean = false,
    val encrypt: Boolean? = null,
    val auto: Boolean = true,
    val sealedKey: String? = null,
)
