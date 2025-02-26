package com.folioreader.util

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * @author Tyler Sedlar
 */
object FontFinder {

    private var sysFonts: Map<String, File>? = null
    private var userFonts: Map<String, File>? = null
    private var assetFonts: Map<String, String>? = null
    private lateinit var appContext: Context // Store application context safely

    // Supported font suffixes
    private val fontSuffixes = listOf(".ttf", ".otf") // Add more if needed, e.g., ".ttc"

    // Initialize with application context
    @JvmStatic
    fun initialize(context: Context) {
        appContext = context.applicationContext
        sysFonts = null
        userFonts = null
        assetFonts = null
        Log.d("FontFinder", "Initialized with context: ${appContext.packageName}")
    }

    private fun ensureInitialized() {
        if (!::appContext.isInitialized) {
            throw IllegalStateException("FontFinder must be initialized with a Context before use. Call FontFinder.initialize(context) first.")
        }
    }

    // System fonts
    @JvmStatic
    fun getSystemFonts(): Map<String, File> {
        if (sysFonts != null) {
            Log.d("FontFinder", "Returning cached system fonts: $sysFonts")
            return sysFonts!!
        }

        val fonts = HashMap<String, File>()
        val sysFontDir = File("/system/fonts/")
        Log.d("FontFinder", "Scanning system fonts directory: ${sysFontDir.absolutePath}, exists: ${sysFontDir.exists()}")

        val files = sysFontDir.listFiles() ?: emptyArray()
        Log.d("FontFinder", "Found ${files.size} files in /system/fonts/")
        for (fontFile in files) {
            val fontName = fontFile.name
            fontSuffixes.forEach { suffix ->
                if (fontName.endsWith(suffix)) {
                    val key = fontName.subSequence(0, fontName.lastIndexOf(suffix)).toString()
//                    fonts[key] = fontFile
                    Log.d("FontFinder", "Added system font: $key -> ${fontFile.absolutePath}")
                }
            }
        }

        sysFonts = fonts
        Log.d("FontFinder", "System fonts loaded: $fonts")
        return fonts
    }

    // User fonts
    @JvmStatic
    fun getUserFonts(): Map<String, File> {
        if (userFonts != null) {
            Log.d("FontFinder", "Returning cached user fonts: $userFonts")
            return userFonts!!
        }

        val fonts = HashMap<String, File>()
        val fontDirs = arrayOf(File(Environment.getExternalStorageDirectory(), "Fonts/"))
        Log.d("FontFinder", "Scanning user fonts directories: ${fontDirs.joinToString()}")

        fontDirs.forEach { fontDir ->
            if (fontDir.exists() && fontDir.isDirectory) {
                Log.d("FontFinder", "Exploring directory: ${fontDir.absolutePath}")
                fontDir.walkTopDown()
                    .filter { f -> fontSuffixes.any { suffix -> f.name.endsWith(suffix) } }
                    .forEach { fontFile ->
                        val fontName = fontFile.name
                        val suffix = fontSuffixes.find { fontName.endsWith(it) } ?: return@forEach
                        val key = fontName.subSequence(0, fontName.lastIndexOf(suffix)).toString()
                        fonts[key] = fontFile
                        Log.d("FontFinder", "Added user font: $key -> ${fontFile.absolutePath}")
                    }
            } else {
                Log.d("FontFinder", "Directory does not exist or is not a directory: ${fontDir.absolutePath}")
            }
        }

        userFonts = fonts
        Log.d("FontFinder", "User fonts loaded: $fonts")
        return fonts
    }

    // Fetch fonts from assets/fonts/ with recursive subdirectory support
    @JvmStatic
    fun getAssetFonts(): Map<String, String> {
        ensureInitialized()

        if (assetFonts != null) {
            Log.d("FontFinder", "Returning cached asset fonts: $assetFonts")
            return assetFonts!!
        }

        val fonts = HashMap<String, String>()
        val assetManager = appContext.assets
        Log.d("FontFinder", "Scanning assets for fonts with AssetManager")

        try {
            collectAssetFonts(assetManager, "fonts", fonts)
        } catch (e: IOException) {
            Log.e("FontFinder", "Error loading asset fonts: ${e.message}", e)
        }

        assetFonts = fonts
        Log.d("FontFinder", "Asset fonts loaded: $fonts")
        return fonts
    }

    // Helper method to recursively collect fonts
    private fun collectAssetFonts(assetManager: android.content.res.AssetManager, path: String, fonts: MutableMap<String, String>) {
        try {
            val files = assetManager.list(path) ?: emptyArray()
            Log.d("FontFinder", "Listing assets in path: $path, found ${files.size} items: ${files.joinToString()}")

            for (fileName in files) {
                val fullPath = if (path.isEmpty()) fileName else "$path/$fileName"
                if (fontSuffixes.any { fileName.endsWith(it) }) {
                    val suffix = fontSuffixes.find { fileName.endsWith(it) } ?: continue
                    val key = fileName.subSequence(0, fileName.lastIndexOf(suffix)).toString()
                    fonts[key] = fullPath
                    Log.d("FontFinder", "Added asset font: $key -> $fullPath")
                } else {
                    Log.d("FontFinder", "$fileName is not a font file, checking as directory")
                    collectAssetFonts(assetManager, fullPath, fonts)
                }
            }
        } catch (e: IOException) {
            Log.e("FontFinder", "Error listing assets in $path: ${e.message}", e)
        }
    }

    // Unified getFontFile including assets
    @JvmStatic
    fun getFontFile(key: String): File? {
        ensureInitialized()
        Log.d("FontFinder", "Looking for font with key: $key")

        val system = getSystemFonts()
        val user = getUserFonts()
        val assets = getAssetFonts()

        return when {
            system.containsKey(key) -> {
                Log.d("FontFinder", "Found $key in system fonts")
                system[key]
            }
            user.containsKey(key) -> {
                Log.d("FontFinder", "Found $key in user fonts")
                user[key]
            }
            assets.containsKey(key) -> {
                Log.d("FontFinder", "Found $key in asset fonts, converting to file")
                assets[key]?.let { assetPath ->
                    try {
                        val suffix = fontSuffixes.find { assetPath.endsWith(it) } ?: ".ttf"
                        val tempFile = File(appContext.cacheDir, "$key$suffix")
                        Log.d("FontFinder", "Copying $assetPath to ${tempFile.absolutePath}")
                        appContext.assets.open(assetPath).use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.d("FontFinder", "Successfully copied $key to ${tempFile.absolutePath}")
                        tempFile
                    } catch (e: IOException) {
                        Log.e("FontFinder", "Error converting asset font $key to file: ${e.message}", e)
                        null
                    }
                }
            }
            else -> {
                Log.d("FontFinder", "Font $key not found in system, user, or assets")
                null
            }
        }
    }

    @JvmStatic
    fun isSystemFont(key: String): Boolean {
        return getSystemFonts().containsKey(key)
    }

    @JvmStatic
    fun isAssetFont(key: String): Boolean {
        ensureInitialized()
        return getAssetFonts().containsKey(key)
    }
}