package com.example.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * لایه‌ی سازگاری پلتفرم. در اندروید فایل‌ها با Uri می‌آمدند؛ روی دسکتاپ
 * مسیر واقعی فایل داریم، پس Uri همان File است و بقیه‌ی کد بدون تغییر می‌ماند.
 */
typealias Uri = File

object ActivityResultContracts {
    class OpenDocument
    class CreateDocument(val mimeType: String = "*/*")
}

interface FileLauncher<I> {
    fun launch(input: I)
}

@Composable
fun rememberLauncherForActivityResult(
    contract: ActivityResultContracts.OpenDocument,
    onResult: (File?) -> Unit
): FileLauncher<Array<String>> {
    val current by rememberUpdatedState(onResult)
    return remember {
        object : FileLauncher<Array<String>> {
            override fun launch(input: Array<String>) = current(pickFile(save = false, name = null))
        }
    }
}

@Composable
fun rememberLauncherForActivityResult(
    contract: ActivityResultContracts.CreateDocument,
    onResult: (File?) -> Unit
): FileLauncher<String> {
    val current by rememberUpdatedState(onResult)
    return remember {
        object : FileLauncher<String> {
            override fun launch(input: String) = current(pickFile(save = true, name = input))
        }
    }
}

private fun pickFile(save: Boolean, name: String?): File? {
    val dialog = FileDialog(null as Frame?, if (save) "ذخیره" else "انتخاب فایل", if (save) FileDialog.SAVE else FileDialog.LOAD)
    if (name != null) dialog.file = name
    dialog.isVisible = true
    val f = dialog.file ?: return null
    return File(dialog.directory, f)
}

/** باز کردن آدرس در مرورگر پیش‌فرض ویندوز. */
fun openUrl(url: String): Boolean = try {
    java.awt.Desktop.getDesktop().browse(java.net.URI(url)); true
} catch (_: Exception) { false }

object AppLog {
    fun w(tag: String, msg: String) = System.err.println("W/$tag: $msg")
    fun e(tag: String, msg: String) = System.err.println("E/$tag: $msg")
    fun d(tag: String, msg: String) = System.err.println("D/$tag: $msg")
}

/** جایگزین android.content.Context؛ فقط همان بخشی را دارد که کد منطقی لازم دارد. */
class Context {
    val contentResolver = ContentResolver
}

object ContentResolver {
    fun openInputStream(file: File): java.io.InputStream? =
        if (file.isFile) file.inputStream() else null

    fun openOutputStream(file: File): java.io.OutputStream? = file.outputStream()
}

/** جایگزین ساده‌ی androidx.lifecycle.ViewModel (کتابخانه‌ی androidx در این ساخت نیست). */
abstract class ViewModel {
    val viewModelScope: kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)

    protected open fun onCleared() {}
    fun clear() { viewModelScope.cancel(); onCleared() }
}
