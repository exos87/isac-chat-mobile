package sk.uss.isac.chat.mobile.app

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class AppForegroundCoordinatorTest {

    @Test
    fun `increments activation counter on start`() {
        val coordinator = AppForegroundCoordinator()

        coordinator.onStart(FakeLifecycleOwner())
        coordinator.onStart(FakeLifecycleOwner())

        assertEquals(2L, (coordinator.activations as StateFlow<Long>).value)
    }

    private class FakeLifecycleOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }
}
