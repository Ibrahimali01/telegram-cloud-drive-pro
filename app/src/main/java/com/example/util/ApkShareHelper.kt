package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ApkShareHelper {

    /**
     * Extracts the running app's installed APK file directly from the Android base APK path,
     * copies it securely to the app cache directory, and invokes the Android System Share Sheet
     * with the exact MIME type `application/vnd.android.package-archive` to share the real APK file.
     */
    fun shareInstalledApk(context: Context, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        try {
            val appInfo = context.applicationInfo
            val sourceApkFile = File(appInfo.sourceDir)

            if (!sourceApkFile.exists()) {
                val errorMsg = "تعذر العثور على ملف الـ APK المثبت"
                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                onResult(false, errorMsg)
                return
            }

            // Create target cache dir for APK
            val apkCacheDir = File(context.cacheDir, "apk")
            if (!apkCacheDir.exists()) {
                apkCacheDir.mkdirs()
            }

            // Clean old exported apks
            apkCacheDir.listFiles()?.forEach { it.delete() }

            val appName = "TeleDrive"
            val targetApkFile = File(apkCacheDir, "${appName}_v1.0.apk")

            // Stream copy source APK into cache
            FileInputStream(sourceApkFile).use { input ->
                FileOutputStream(targetApkFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            // Generate content URI via FileProvider
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetApkFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_SUBJECT, "تطبيق TeleDrive APK")
                putExtra(Intent.EXTRA_TEXT, "تفضل بتثبيت تطبيق TeleDrive - مساحتك السحابية غير المحدودة والآمنة 🚀")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "إرسال وتثبيت ملف APK عبر...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(chooser)
            onResult(true, "تم تجهيز ومشاركة ملف APK بنجاح 📦")
        } catch (e: Exception) {
            val errorMsg = "حدث خطأ أثناء استخراج ملف الـ APK: ${e.localizedMessage}"
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            onResult(false, errorMsg)
        }
    }
}
