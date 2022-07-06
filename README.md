# ScopedViewModel
Scoping ViewModel to a @Composable function example


# Usage
Add this files to your project and then call scopedViewModel() or rememberScoped() fun if you need a scoped ViewModel

```kotlin
@Composable
fun YourFunction(
  viewModel: YourViewModel = scopedViewModel()
) {
  /*use your scoped viewModel here*/
}
```
