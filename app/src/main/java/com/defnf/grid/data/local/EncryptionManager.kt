package com.defnf.grid.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionManager @Inject constructor() {
    
    private val keyAlias = "grid_credential_key"
    
    private val keyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }
    
    private fun generateKeyIfNeeded() {
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(false)
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }
    
    private fun getSecretKey(): SecretKey {
        generateKeyIfNeeded()
        return keyStore.getKey(keyAlias, null) as SecretKey
    }
    
    fun encrypt(data: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data.toByteArray())
        
        // Combine IV and encrypted data
        val combined = iv + encryptedData
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }
    
    fun decrypt(encryptedData: String): String {
        val combined = Base64.decode(encryptedData, Base64.DEFAULT)
        
        // Extract IV and encrypted data
        val iv = combined.sliceArray(0..15) // AES block size is 16 bytes
        val encrypted = combined.sliceArray(16 until combined.size)
        
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), IvParameterSpec(iv))
        
        val decryptedData = cipher.doFinal(encrypted)
        return String(decryptedData)
    }
    
    fun isKeyAvailable(): Boolean {
        return keyStore.containsAlias(keyAlias)
    }
}