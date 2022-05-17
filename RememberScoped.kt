import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

@Composable
inline fun <reified VM : ViewModel> scopedViewModel(): VM {
    return rememberScoped { viewModel() }
}

@Composable
fun <VM : ViewModel> rememberScoped(key: Any? = null, builder: @Composable () -> VM): VM {
    val scopedViewModelContainer: ScopedViewModelContainer = viewModel()

    val viewModelStore = LocalViewModelStoreOwner.current?.viewModelStore

    ObserveLifecycleWithScopedViewModelContainer(scopedViewModelContainer)

    val internalKey = rememberSaveable { UUID.randomUUID().toString() }
    val externalKey = ScopedViewModelContainer.ExternalKey.from(key)

    val scopedViewModel: VM = scopedViewModelContainer.getOrBuildViewModel(
        key = internalKey,
        externalKey = externalKey,
        builder = builder
    )

    DisposableEffect(internalKey) {
        onDispose {
            scopedViewModelContainer.onDisposedFromComposition(
                key = internalKey,
                viewModelStore = viewModelStore!!
            )
        }
    }

    return scopedViewModel
}

@Composable
private fun ObserveLifecycleWithScopedViewModelContainer(scopedViewModelContainer: ScopedViewModelContainer) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(lifecycle, scopedViewModelContainer) {
        launch(Dispatchers.Main) {
            lifecycle.addObserver(scopedViewModelContainer)
        }
    }
}