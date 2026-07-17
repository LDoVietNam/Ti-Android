package ti.android.app.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdentityStore @Inject constructor(
    private val context: Context
) {
    private val prefs = context.getSharedPreferences("ti_device_identity", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val KEY_ALIAS = "ti-android-device-key"

    /**
     * Get or create a persistent device ID.
     */
    fun getDeviceId(): String {
        val existing = prefs.getString("device_id", null)
        if (existing != null) return existing

        val newId = "ti-android-${UUID.randomUUID().toString().take(8)}"
        prefs.edit().putString("device_id", newId).apply()
        return newId
    }

    /**
     * Generate and store device key pair in Android Keystore.
     */
    fun generateKeyPair() {
        if (keyStore.containsAlias(KEY_ALIAS)) return

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .build()

        generator.initialize(spec)
        generator.generateKeyPair()
    }

    fun hasKeyPair(): Boolean = keyStore.containsAlias(KEY_ALIAS)
}
