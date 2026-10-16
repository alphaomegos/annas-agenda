package com.alphaomegos.annasagenda.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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
 */
internal object KeystoreSidecarSealer : SidecarSealer {
    private const val ALIAS = "annas_agenda_sidecar_session"
    private const val STORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    private fun store(): KeyStore = KeyStore.getInstance(STORE).apply { load(null) }

    private fun key(): SecretKey {
        (store().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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
        runCatching { store().deleteEntry(ALIAS) }
    }
}

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
        instance ?: MusicLibrary(SidecarConnections.of(context)).also { instance = it }
    }
}
