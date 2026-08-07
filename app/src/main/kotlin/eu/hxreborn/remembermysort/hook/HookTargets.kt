package eu.hxreborn.remembermysort.hook

import android.database.Cursor
import java.lang.reflect.Method

internal object HookTargets {
    const val SORT_MODEL = "com.android.documentsui.sorting.SortModel"

    private const val LOOKUP = "com.android.documentsui.base.Lookup"

    val PACKAGES =
        setOf(
            "com.google.android.documentsui",
            "com.android.documentsui",
        )

    val SORT_FRAGMENT_CLASSES =
        listOf(
            "com.android.documentsui.sorting.SortListFragment",
            "com.google.android.documentsui.sorting.SortListFragment",
        )

    val LOADERS: List<Pair<String, LoaderHook>> =
        listOf(
            "com.android.documentsui.DirectoryLoader" to DirectoryLoaderHook,
            "com.android.documentsui.loaders.FolderLoader" to FolderLoaderHook,
            "com.android.documentsui.RecentsLoader" to RecentsLoaderHook,
        )

    fun sortCursor(classLoader: ClassLoader): Method =
        classLoader
            .loadClass(SORT_MODEL)
            .getDeclaredMethod("sortCursor", Cursor::class.java, classLoader.loadClass(LOOKUP))

    fun loadInBackground(
        classLoader: ClassLoader,
        className: String,
    ): Method = classLoader.loadClass(className).getDeclaredMethod("loadInBackground")

    fun lifecycle(
        classLoader: ClassLoader,
        className: String,
        methodName: String,
    ): Method = classLoader.loadClass(className).getMethod(methodName)
}
