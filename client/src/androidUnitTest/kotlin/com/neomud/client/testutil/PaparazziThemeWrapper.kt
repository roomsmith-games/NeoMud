package com.neomud.client.testutil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.LocalResourceReader
import org.jetbrains.compose.resources.ResourceReader

/**
 * Drop-in replacement for TestThemeWrapper in Paparazzi screenshot tests.
 *
 * CMP 1.10.2 changed DefaultAndroidResourceReader to eagerly call
 * InstrumentationRegistry.getInstrumentation() (without a fallback), which throws in
 * Paparazzi's JVM rendering environment. We bypass it by providing LocalResourceReader with
 * a reader backed by Paparazzi's own context AssetManager, which has the compiled CMP resources.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun PaparazziThemeWrapper(content: @Composable () -> Unit) {
    val context = LocalContext.current
    CompositionLocalProvider(LocalResourceReader provides AssetManagerResourceReader(context)) {
        TestThemeWrapper(content)
    }
}

@OptIn(ExperimentalResourceApi::class)
private class AssetManagerResourceReader(
    private val context: android.content.Context
) : ResourceReader {

    override suspend fun read(path: String): ByteArray =
        context.assets.open(path).use { it.readBytes() }

    override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray =
        context.assets.open(path).use { stream ->
            stream.skip(offset)
            ByteArray(size.toInt()).also { buf -> stream.read(buf) }
        }

    override fun getUri(path: String): String = "file:///android_asset/$path"
}
