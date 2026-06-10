package sk.uss.isac.chat.mobile.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AppForegroundEvents {
    val activations: Flow<Long>
}

class AppForegroundCoordinator : DefaultLifecycleObserver, AppForegroundEvents {
    private val _activations = MutableStateFlow(0L)
    override val activations: Flow<Long> = _activations.asStateFlow()

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        _activations.value = _activations.value + 1
    }
}
