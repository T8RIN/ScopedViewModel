package ru.tech.cookhelper.presentation.ui.utils.scope

import androidx.compose.runtime.Composable
import androidx.lifecycle.*
import androidx.lifecycle.ViewModelClearer.clearViewModel
import androidx.lifecycle.ViewModelClearer.getPrivateProperty
import androidx.lifecycle.ViewModelClearer.setAndReturnPrivateProperty
import androidx.savedstate.SavedStateRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentSkipListSet

class ScopedViewModelContainer : ViewModel(), LifecycleEventObserver {

    private var viewModelStore: ViewModelStore? = null

    private var savedStateRegistry: SavedStateRegistry? = null

    private var isInForeground = true

    private val scopedViewModelsKeys = mutableMapOf<String, ExternalKey>()

    private val scopedViewModelsContainer = mutableMapOf<String, ViewModel>()

    private val markedForDisposal = ConcurrentSkipListSet<String>()

    private val disposingJobs = mutableMapOf<String, Job>()

    private val disposeDelayTimeMillis: Long = 1000

    @Suppress("UNCHECKED_CAST")
    @Composable
    fun <VM : ViewModel> getOrBuildViewModel(
        key: String,
        externalKey: ExternalKey = ExternalKey(0),
        builder: @Composable () -> VM
    ): VM {
        @Composable
        fun buildAndStoreViewModel() =
            builder.invoke().apply { scopedViewModelsContainer[key] = this }

        cancelDisposal(key)

        return if (scopedViewModelsKeys.containsKey(key) && (scopedViewModelsKeys[key] == externalKey)) {
            scopedViewModelsContainer[key] as? VM ?: buildAndStoreViewModel()
        } else {
            scopedViewModelsKeys[key] = externalKey
            buildAndStoreViewModel()
        }
    }

    fun onDisposedFromComposition(key: String, viewModelStore: ViewModelStore, savedStateRegistry: SavedStateRegistry) {
        this.viewModelStore = viewModelStore
        this.savedStateRegistry = savedStateRegistry
        markedForDisposal.add(key)
        scheduleToDisposeBeforeGoingToBackground(key)
    }

    private fun scheduleToDisposeBeforeGoingToBackground(key: String) = scheduleToDispose(key = key)

    private fun scheduleToDisposeAfterReturningFromBackground() {
        markedForDisposal.forEach { key -> scheduleToDispose(key) }
    }

    private fun alreadyDisposing(key: String): Boolean {
        return disposingJobs.containsKey(key)
    }

    private fun scheduleToDispose(
        key: String,
        removalCondition: () -> Boolean = { isInForeground }
    ) {
        if (alreadyDisposing(key)) return

        val newDisposingJob = viewModelScope.launch {
            delay(disposeDelayTimeMillis)
            if (removalCondition()) {
                markedForDisposal.remove(key)
                scopedViewModelsContainer.remove(key)
                    ?.also {
                        if (shouldClearDisposedViewModel(it)) clearDisposedViewModel(it)
                    }
            }
            disposingJobs.remove(key)
        }
        disposingJobs[key] = newDisposingJob
    }

    private fun shouldClearDisposedViewModel(disposedViewModel: ViewModel): Boolean =
        !scopedViewModelsContainer.containsValue(disposedViewModel)

    @Suppress("UNCHECKED_CAST")
    private fun clearDisposedViewModel(scopedViewModel: ViewModel) {
        val name = scopedViewModel.javaClass.name
        val mMap = viewModelStore.getPrivateProperty("mMap") as HashMap<String, ViewModel>
        val key = "$TAG:$name"
        mMap[key]?.clearViewModel()
        mMap.remove(key)
        viewModelStore.setAndReturnPrivateProperty("mMap", mMap)
        savedStateRegistry?.unregisterSavedStateProvider(name)
    }

    private fun cancelDisposal(key: String) {
        disposingJobs.remove(key)?.cancel()
        markedForDisposal.remove(key)
    }

    override fun onCleared() {
        disposingJobs.forEach { (_, job) -> job.cancel() }
        scopedViewModelsContainer.values.forEach { clearDisposedViewModel(it) }
        scopedViewModelsContainer.clear()
        super.onCleared()
    }

    override fun onStateChanged(lifecycleOwner: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                isInForeground = true
                scheduleToDisposeAfterReturningFromBackground()
            }
            Lifecycle.Event.ON_PAUSE -> {
                isInForeground = false
            }
            Lifecycle.Event.ON_DESTROY -> {
                lifecycleOwner.lifecycle.removeObserver(this)
            }
            else -> { /* the other lifecycle event are irrelevant */
            }
        }
    }

    @JvmInline
    value class ExternalKey(val value: Int) {
        companion object {
            fun from(objectInstance: Any?): ExternalKey = ExternalKey(objectInstance.hashCode())
        }
    }

    companion object {
        private const val TAG = "androidx.lifecycle.ViewModelProvider.DefaultKey"
    }
}