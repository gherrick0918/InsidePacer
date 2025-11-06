package app.insidepacer.backup.ui

import android.app.Activity
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

object ActivityTracker {
    private val ref = AtomicReference<WeakReference<Activity>?>(null)

    fun onStart(activity: Activity) {
        ref.set(WeakReference(activity))
    }

    fun onStop(activity: Activity) {
        ref.get()?.let { weak ->
            if (weak.get() == activity) {
                ref.set(null)
            }
        }
    }

    fun currentActivity(): Activity? = ref.get()?.get()
}
