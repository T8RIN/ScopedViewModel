package androidx.lifecycle

/* this package used to access VM.clear() package private method */
internal object ViewModelClearer {
    fun ViewModel.clearViewModel() = clear()

    fun <T : Any?> T.getPrivateProperty(variableName: String): Any? {
        if (this == null) return null

        return javaClass.getDeclaredField(variableName).let { field ->
            field.isAccessible = true
            return@let field.get(this)
        }
    }

    fun <T : Any?> T.setAndReturnPrivateProperty(variableName: String, data: Any): Any? {
        if (this == null) return null

        return javaClass.getDeclaredField(variableName).let { field ->
            field.isAccessible = true
            field.set(this, data)
            return@let field.get(this)
        }
    }
}