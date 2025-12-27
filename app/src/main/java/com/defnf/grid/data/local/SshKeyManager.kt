package com.defnf.grid.data.local

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.StringWriter
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptionManager: EncryptionManager
) {
    private val prefs by lazy {
        context.getSharedPreferences("ssh_keys", Context.MODE_PRIVATE)
    }

    /**
     * Generate a new RSA keypair for the given key name
     * @param keyName Unique identifier for the key (e.g., "default" or connection ID)
     * @param keySize RSA key size (2048 or 4096 recommended)
     * @return The public key in OpenSSH format for adding to authorized_keys
     */
    fun generateKeyPair(keyName: String, keySize: Int = 4096): String {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(keySize)
        val keyPair = keyPairGenerator.generateKeyPair()

        // Store the private key encrypted
        val privateKeyPem = privateKeyToPem(keyPair)
        val encryptedPrivateKey = encryptionManager.encrypt(privateKeyPem)
        prefs.edit().putString("private_key_$keyName", encryptedPrivateKey).apply()

        // Generate and store the public key in OpenSSH format
        val publicKeyOpenSsh = publicKeyToOpenSsh(keyPair.public as RSAPublicKey, keyName)
        prefs.edit().putString("public_key_$keyName", publicKeyOpenSsh).apply()

        println("SshKeyManager: Generated $keySize-bit RSA keypair for '$keyName'")
        return publicKeyOpenSsh
    }

    /**
     * Get the private key in PEM format for authentication
     */
    fun getPrivateKey(keyName: String): String? {
        val encryptedKey = prefs.getString("private_key_$keyName", null) ?: return null
        return try {
            encryptionManager.decrypt(encryptedKey)
        } catch (e: Exception) {
            println("SshKeyManager: Failed to decrypt private key: ${e.message}")
            null
        }
    }

    /**
     * Get the public key in OpenSSH format for copying to server
     */
    fun getPublicKey(keyName: String): String? {
        return prefs.getString("public_key_$keyName", null)
    }

    /**
     * Check if a keypair exists for the given name
     */
    fun hasKeyPair(keyName: String): Boolean {
        return prefs.contains("private_key_$keyName")
    }

    /**
     * Delete a keypair
     */
    fun deleteKeyPair(keyName: String) {
        prefs.edit()
            .remove("private_key_$keyName")
            .remove("public_key_$keyName")
            .apply()
        println("SshKeyManager: Deleted keypair '$keyName'")
    }

    /**
     * List all stored key names
     */
    fun listKeyNames(): List<String> {
        return prefs.all.keys
            .filter { it.startsWith("private_key_") }
            .map { it.removePrefix("private_key_") }
    }

    /**
     * Convert private key to PEM format
     */
    private fun privateKeyToPem(keyPair: KeyPair): String {
        val privateKeyBytes = keyPair.private.encoded
        val base64 = Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP)

        // Format as PEM with 64-character lines
        val pemBody = base64.chunked(64).joinToString("\n")
        return "-----BEGIN PRIVATE KEY-----\n$pemBody\n-----END PRIVATE KEY-----"
    }

    /**
     * Convert RSA public key to OpenSSH format (ssh-rsa AAAA... keyname)
     */
    private fun publicKeyToOpenSsh(publicKey: RSAPublicKey, keyName: String): String {
        val byteArrayOutputStream = java.io.ByteArrayOutputStream()

        // Write key type
        val keyType = "ssh-rsa".toByteArray()
        writeBytes(byteArrayOutputStream, keyType)

        // Write public exponent
        val exponent = publicKey.publicExponent.toByteArray()
        writeBytes(byteArrayOutputStream, exponent)

        // Write modulus
        val modulus = publicKey.modulus.toByteArray()
        writeBytes(byteArrayOutputStream, modulus)

        val keyData = Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP)
        return "ssh-rsa $keyData grid-$keyName"
    }

    private fun writeBytes(out: java.io.ByteArrayOutputStream, bytes: ByteArray) {
        // Write length as 4-byte big-endian integer
        val length = bytes.size
        out.write((length shr 24) and 0xFF)
        out.write((length shr 16) and 0xFF)
        out.write((length shr 8) and 0xFF)
        out.write(length and 0xFF)
        // Write the bytes
        out.write(bytes)
    }
}
