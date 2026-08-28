package com.oblador.keychain.resultHandler

import android.os.Looper
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.AssertionException
import com.facebook.react.bridge.ReactApplicationContext
import com.oblador.keychain.DeviceAvailability
import com.oblador.keychain.cipherStorage.CipherStorage
import com.oblador.keychain.cipherStorage.CipherStorage.DecryptionResult
import com.oblador.keychain.cipherStorage.CipherStorage.EncryptionResult
import com.oblador.keychain.cipherStorage.CipherStorageBase
import com.oblador.keychain.exceptions.CryptoFailedException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

open class ResultHandlerInteractiveBiometric(
  protected val reactContext: ReactApplicationContext,
  storage: CipherStorage,
  protected var promptInfo: BiometricPrompt.PromptInfo
) : BiometricPrompt.AuthenticationCallback(), ResultHandler {

  // Explicitly declare the visibility and use 'override' to match the interface
  override var decryptionResult: DecryptionResult? = null
  override var encryptionResult: EncryptionResult? = null
  override var error: Throwable? = null
  protected val storage: CipherStorageBase = storage as CipherStorageBase
  // Android may deliver a final callback after cancellation. Discard callbacks after shutdown.
  protected val executor = ThreadPoolExecutor(
    1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue(),
    ThreadPoolExecutor.DiscardPolicy()
  )
  @Volatile
  protected var context: CryptoContext? = null
  @Volatile
  protected var presentedPrompt: BiometricPrompt? = null

  // Synchronization primitives
  private val lock = ReentrantLock()
  private val condition = lock.newCondition()
  protected val isComplete: Boolean get() = lock.withLock { completed }
  private var completed = false

  /** Logging tag. */
  protected val LOG_TAG = ResultHandlerInteractiveBiometric::class.java.simpleName

  override fun askAccessPermissions(context: CryptoContext) {
    this.context = context

    if (!DeviceAvailability.isPermissionsGranted(reactContext)) {
      val failure = CryptoFailedException(
        "Could not start biometric Authentication. No permissions granted."
      )
      when (context.operation) {
        CryptoOperation.ENCRYPT -> onEncrypt(null, failure)
        CryptoOperation.DECRYPT -> onDecrypt(null, failure)
      }
    } else {
      startAuthentication()
    }
  }

  override fun onEncrypt(encryptionResult: EncryptionResult?, error: Throwable?) {
    lock.withLock {
      if (completed) return
      this.encryptionResult = encryptionResult
      this.error = error
      complete()
    }
  }

  override fun onDecrypt(
    decryptionResult: DecryptionResult?, error: Throwable?
  ) {
    lock.withLock {
      if (completed) return
      this.decryptionResult = decryptionResult
      this.error = error
      complete()
    }
  }

  // Called with lock held; publishing a terminal result and releasing the waiter are atomic.
  private fun complete() {
    completed = true
    context = null
    presentedPrompt = null
    executor.shutdown()
    condition.signalAll()
  }

  protected fun failAuthentication(error: Throwable) {
    when (context?.operation) {
      CryptoOperation.ENCRYPT -> onEncrypt(null, error)
      CryptoOperation.DECRYPT, null -> onDecrypt(null, error)
    }
  }

  /** Called when an unrecoverable error has been encountered and the operation is complete. */
  override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
    val error = CryptoFailedException("code: $errorCode, msg: $errString")
    failAuthentication(error)
  }

  /** Called when a biometric is recognized. */
  override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
    if (isComplete) return
    val context = context ?: return
    try {
      when (context.operation) {
        CryptoOperation.ENCRYPT -> {
          val encrypted = EncryptionResult(
            storage.encryptString(context.key, String(context.username)),
            storage.encryptString(context.key, String(context.password)),
            storage
          )
          onEncrypt(encrypted, null)
        }

        CryptoOperation.DECRYPT -> {
          val decrypted = DecryptionResult(
            storage.decryptBytes(context.key, context.username),
            storage.decryptBytes(context.key, context.password)
          )
          onDecrypt(decrypted, null)
        }
      }
    } catch (fail: Throwable) {
      failAuthentication(fail)
    }
  }


  /** Trigger interactive authentication. */
  open fun startAuthentication() {
    if (isComplete) return
    try {
      val activity = getCurrentActivity()
      // Code can be executed only from MAIN thread.
      if (Thread.currentThread() != Looper.getMainLooper().thread) {
        activity.runOnUiThread { startAuthentication() }
        waitResult()
        return
      }
      val prompt = authenticateWithPrompt(activity)
      val cancelPrompt = lock.withLock {
        if (completed) true else {
          presentedPrompt = prompt
          false
        }
      }
      if (cancelPrompt) prompt.cancelAuthentication()
    } catch (fail: Throwable) {
      failAuthentication(fail)
    }
  }

  protected fun getCurrentActivity(): FragmentActivity {
    val activity = reactContext.currentActivity as? FragmentActivity
    return activity ?: throw NullPointerException("Not assigned current activity")
  }

  protected fun authenticateWithPrompt(activity: FragmentActivity): BiometricPrompt {
    val prompt = BiometricPrompt(activity, executor, this)
    prompt.authenticate(this.promptInfo)
    return prompt
  }

  /** Block current NON-main thread and wait for user authentication results. */
  override fun waitResult() {
    if (Thread.currentThread() == Looper.getMainLooper().thread) {
      throw AssertionException("method should not be executed from MAIN thread")
    }

    Log.i(LOG_TAG, "blocking thread. waiting for done UI operation.")

    try {
      lock.withLock {
        while (!completed) {
          condition.await()
        }
      }
    } catch (interrupted: InterruptedException) {
      val prompt = presentedPrompt
      failAuthentication(CryptoFailedException("Authentication interrupted", interrupted))
      try {
        (reactContext.currentActivity as? FragmentActivity)?.runOnUiThread {
          try {
            prompt?.cancelAuthentication()
          } catch (fail: Exception) {
            Log.w(LOG_TAG, "Could not cancel authentication: ${fail.message}")
          }
        }
      } finally {
        Thread.currentThread().interrupt()
      }
    }

    Log.i(LOG_TAG, "unblocking thread.")
  }
}
