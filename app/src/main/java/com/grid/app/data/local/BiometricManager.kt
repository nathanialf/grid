package com.grid.app.data.local

import android.content.Context
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.grid.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class BiometricManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    fun isBiometricAvailable(): Boolean {
        val biometricManager = androidx.biometric.BiometricManager.from(context)
        val result = biometricManager.canAuthenticate(BIOMETRIC_WEAK)
        println("Grid BiometricManager: Checking availability - Result: $result")
        return when (result) {
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS -> {
                println("Grid BiometricManager: Biometric available")
                true
            }
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                println("Grid BiometricManager: No biometric hardware")
                false
            }
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                println("Grid BiometricManager: Biometric hardware unavailable")
                false
            }
            androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                println("Grid BiometricManager: No biometric enrolled")
                false
            }
            else -> {
                println("Grid BiometricManager: Other biometric error: $result")
                false
            }
        }
    }
    
    suspend fun authenticate(activity: FragmentActivity): Result<Boolean> = suspendCancellableCoroutine { continuation ->
        println("Grid BiometricManager: Starting authentication")
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    println("Grid BiometricManager: Authentication error - Code: $errorCode, Message: $errString")
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception(errString.toString())))
                    }
                }
                
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    println("Grid BiometricManager: Authentication succeeded")
                    if (continuation.isActive) {
                        continuation.resume(Result.success(true))
                    }
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    println("Grid BiometricManager: Authentication failed")
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception("Authentication failed")))
                    }
                }
            })
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.biometric_prompt_title))
            .setSubtitle(context.getString(R.string.biometric_prompt_subtitle))
            .setDescription(context.getString(R.string.biometric_prompt_description))
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .setNegativeButtonText("Cancel")
            .build()
        
        continuation.invokeOnCancellation {
            println("Grid BiometricManager: Authentication cancelled")
            biometricPrompt.cancelAuthentication()
        }
        
        println("Grid BiometricManager: Calling biometricPrompt.authenticate()")
        biometricPrompt.authenticate(promptInfo)
    }
}