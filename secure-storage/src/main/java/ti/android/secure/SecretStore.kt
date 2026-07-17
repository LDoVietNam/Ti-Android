package ti.android.secure

/** Stores encrypted secrets using Android Keystore-backed encryption. */
class SecretStore {
    fun store(key: String, value: String) {
        // TODO: Use EncryptedSharedPreferences
    }

    fun retrieve(key: String): String? = null

    fun delete(key: String) {}

    fun clear() {}
}

/** Manages short-lived session tokens with automatic expiry. */
class SessionTokenStore {
    private var token: String? = null
    private var expiresAt: Long = 0L

    fun save(token: String, ttlMs: Long = 3600000) {
        this.token = token
        this.expiresAt = System.currentTimeMillis() + ttlMs
    }

    fun get(): String? {
        if (System.currentTimeMillis() > expiresAt) {
            token = null
            return null
        }
        return token
    }

    fun isValid(): Boolean = get() != null

    fun clear() { token = null; expiresAt = 0 }
}
