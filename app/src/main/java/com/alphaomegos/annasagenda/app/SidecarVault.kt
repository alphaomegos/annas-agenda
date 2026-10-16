package com.alphaomegos.annasagenda.app

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.alphaomegos.annasagenda.support.sidecarBackupDeviceName
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The token sealed with AES-GCM under a key of Android's keystore (16.10):
 * the key never leaves the phone's secure hardware, so a copy of the file
 * taken elsewhere opens nothing. Where the keystore will not do it — a
 * phone that refuses, a test — nothing is sealed, and the session lasts as
 * long as the process does.
 *
 * One key of the keystore for each thing sealed, by [alias]: signing out
 * of the music throws its key away, and must not take the backups' with it.
 */
internal open class KeystoreSealer(private val alias: String) : SidecarSealer {
    private companion object {
        const val STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }

    private fun store(): KeyStore = KeyStore.getInstance(STORE).apply { load(null) }

    private fun key(): SecretKey {
        (store().getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun seal(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(sealed, Base64.NO_WRAP)
    }.getOrNull()

    override fun open(sealed: String): String? = runCatching {
        val iv = Base64.decode(sealed.substringBefore(':'), Base64.NO_WRAP)
        val data = Base64.decode(sealed.substringAfter(':'), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    override fun forget() {
        runCatching { store().deleteEntry(alias) }
    }
}

/** The music's session token (16.10). */
internal object KeystoreSidecarSealer : KeystoreSealer("annas_agenda_sidecar_session")

/** The phone's token for backups, and the key backups are encrypted with (16.10). */
internal object KeystoreBackupSealer : KeystoreSealer("annas_agenda_sidecar_backups")

/** The one sign-in of the process (16.10), for the screens and the player alike. */
internal object SidecarConnections {
    @Volatile
    private var instance: SidecarConnection? = null

    fun of(context: Context): SidecarConnection = instance ?: synchronized(this) {
        instance ?: SidecarConnection(
            file = File(File(context.applicationContext.noBackupFilesDir, "sidecar"), "session.json"),
            sealer = KeystoreSidecarSealer,
            http = SidecarUrlConnection,
        ).also { instance = it }
    }
}

/** The music of the process (16.10), asked through its one sign-in. */
internal object MusicLibraries {
    @Volatile
    private var instance: MusicLibrary? = null

    fun of(context: Context): MusicLibrary = instance ?: synchronized(this) {
        instance ?: MusicLibrary(
            SidecarConnections.of(context),
            MusicLibrarySnapshots(File(File(context.applicationContext.noBackupFilesDir, "music"), "lists")),
        ).also { instance = it }
    }
}

/** Where the player was (16.10), in the no-backup folder. */
internal fun musicPlayerMemoryFile(context: Context): MusicPlayerMemoryFile =
    MusicPlayerMemoryFile(File(File(context.applicationContext.noBackupFilesDir, "music"), "player.json"))

/** The person's own picture from the sidecar (16.10), kept in the no-backup folder. */
internal object SidecarAvatarStores {
    @Volatile
    private var instance: SidecarAvatars? = null

    fun of(context: Context): SidecarAvatars = instance ?: synchronized(this) {
        instance ?: SidecarAvatars(
            dir = File(File(context.applicationContext.noBackupFilesDir, "sidecar"), "avatars"),
            connection = SidecarConnections.of(context),
            fetch = SidecarUrlFetch,
        ).also { instance = it }
    }
}

/** The phone's files standing for tracks (16.10), one record for the process. */
internal object MusicLocalFileStores {
    @Volatile
    private var instance: MusicLocalFiles? = null

    fun of(context: Context): MusicLocalFiles = instance ?: synchronized(this) {
        instance ?: MusicLocalFiles(File(File(context.applicationContext.noBackupFilesDir, "music"), "local-files.json")).also { instance = it }
    }
}

/** The phone's backups in the oursidecar account (16.10), one for the process. */
internal object SidecarBackupAccounts {
    @Volatile
    private var instance: SidecarBackupAccount? = null

    fun of(context: Context): SidecarBackupAccount = instance ?: synchronized(this) {
        instance ?: SidecarBackupAccount(
            file = File(File(context.applicationContext.noBackupFilesDir, "sidecar"), "backups.json"),
            sealer = KeystoreBackupSealer,
            client = SidecarBackupsClient(SidecarBackupUrlConnection),
            deviceName = sidecarBackupDeviceName(Build.MANUFACTURER, Build.MODEL),
        ).also { instance = it }
    }
}

/**
 * Signing in to oursidecar from either screen that offers it (16.10): the
 * music's session, and — with the same password, while it is at hand — the
 * phone's sign-in for backups and their key.
 */
internal suspend fun sidecarSignIn(context: Context, server: String, email: String, password: String): SidecarResult<Unit> {
    val connection = SidecarConnections.of(context)
    val result = connection.signIn(server, email, password)
    if (result is SidecarResult.Ok) SidecarBackupAccounts.of(context).signedIn(connection.status.value.server, email, password)
    return result
}

/** Signing out of oursidecar (16.10): the music, the backups, the picture, and the queue that was this person's. */
internal suspend fun sidecarSignOut(context: Context) {
    SidecarConnections.of(context).signOut()
    SidecarBackupAccounts.of(context).signOut()
    SidecarAvatarStores.of(context).forget()
    musicPlayerMemoryFile(context).forget()
}
