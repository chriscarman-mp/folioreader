/*
 * Copyright (C) 2016 Pedro Paulo de Amorim
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.folioreader.android.sample

import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.folioreader.Config
import com.folioreader.FolioReader
import com.folioreader.FolioReader.OnClosedListener
import com.folioreader.model.HighLight
import com.folioreader.model.HighLight.HighLightAction
import com.folioreader.model.locators.ReadLocator
import com.folioreader.model.locators.ReadLocator.Companion.fromJson
import com.folioreader.util.AppUtil.Companion.getSavedConfig
import com.folioreader.util.OnHighlightListener
import com.folioreader.util.ReadLocatorListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader

class HomeActivity : AppCompatActivity(), OnHighlightListener, ReadLocatorListener,
    OnClosedListener {

    private lateinit var folioReader: FolioReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        folioReader = FolioReader.get()
            .setOnHighlightListener(this)
            .setReadLocatorListener(this)
            .setOnClosedListener(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        highlightsAndSave

        findViewById<View>(R.id.btn_raw).setOnClickListener {
            var config = getSavedConfig(applicationContext)
            if (config == null) config = Config()
            config.allowedDirection = Config.AllowedDirection.VERTICAL_AND_HORIZONTAL
            folioReader.setConfig(config, true)
                .openBook(R.raw.accessible_epub_3)
        }

        findViewById<View>(R.id.btn_assest).setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View) {
                val readLocator: ReadLocator? = lastReadLocator
                var config = getSavedConfig(applicationContext)
                if (config == null) config = Config()
                config!!.isShowBookMarkBtn = true
                config!!.isShowSizeChangerBtn = true
                config!!.isShowSearchBtn = true
                config!!.isShowBackBtn = true
                config!!.allowedDirection = Config.AllowedDirection.VERTICAL_AND_HORIZONTAL
                config!!.setNightThemeColorInt(Color.parseColor("#FFFFFF"))
                config!!.isShowRemainingIndicator = true
                config!!.isShowTextSelection = false
                folioReader.setReadLocator(readLocator)
                folioReader.setConfig(config, true)
                    .openBook("file:///android_asset/the_silver_chair.epub")
            }
        })


        Thread {
            val epubUrl = "https://filesamples.com/samples/ebook/epub/Sway.epub"
//            val epubUrl = "https://hamedtaherpour.github.io/sample-assets/epub/book-8.epub"
//            val epubUrl = "https://filesamples.com/samples/ebook/epub/Around%20the%20World%20in%2028%20Languages.epub"// download error
//            val epubUrl = "https://github.com/IDPF/epub3-samples/releases/download/20170606/haruko-html-jpeg.epub"
//            val epubUrl = "https://github.com/IDPF/epub3-samples/releases/download/20170606/hefty-water.epub"
            val localFilePath = downloadEPUB(epubUrl, "book-8")
            Log.e("112233", "downloaded local file path: $localFilePath")

            runOnUiThread {
                val folioReader = FolioReader.get()
                val config = Config()
                config.isShowBookMarkBtn = false
                config.isShowSizeChangerBtn = false
                config.isShowSearchBtn = false
                config.isShowBackBtn = true
                folioReader.setConfig(config, true)
                    .openBook(localFilePath)
            }

        }.start()
    }

    private fun downloadEPUB(url: String, name: String): String {
        val httpClient = OkHttpClient()

        val request = Request.Builder().url(url).build()

        val response = httpClient.newCall(request).execute()
//        val responseBody = response.body()
        val responseBody: ResponseBody? = response.body()


        val epubFile = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "$name.epub")
        val outputStream = FileOutputStream(epubFile)
        outputStream.write(responseBody?.bytes())

        return epubFile.absolutePath
    }

    private val lastReadLocator: ReadLocator?
        get() {
            val jsonString =
                loadAssetTextAsString("Locators/LastReadLocators/last_read_locator_1.json")
            return fromJson(jsonString)
        }

    override fun saveReadLocator(readLocator: ReadLocator) {
        Log.i(LOG_TAG, "-> saveReadLocator -> " + readLocator.toJson())
    }//You can do anything on successful saving highlight list

    /*
       * For testing purpose, we are getting dummy highlights from asset. But you can get highlights from your server
       * On success, you can save highlights to FolioReader DB.
       */
    private val highlightsAndSave: Unit
        get() {
            Thread {
                var highlightList: ArrayList<HighLight>? = null
                val objectMapper = ObjectMapper()
                try {
                    val jsonString = loadAssetTextAsString("highlights/highlights_data.json")
                    // Use a TypeReference that exactly matches the expected type.
                    highlightList = objectMapper.readValue(
                        jsonString,
                        object : TypeReference<ArrayList<HighLight>>() {}
                    )
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                // Only save if we got a non-null list.
                if (highlightList != null) {
                    folioReader.saveReceivedHighLights(highlightList) {
                        // Callback on successful saving of highlight list
                    }
                }
            }.start()
        }

    private fun loadAssetTextAsString(name: String): String? {
        var `in`: BufferedReader? = null
        try {
            val buf = StringBuilder()
            val `is` = assets.open(name)
            `in` = BufferedReader(InputStreamReader(`is`))
            var str: String?
            var isFirst = true
            while (`in`.readLine().also { str = it } != null) {
                if (isFirst) isFirst = false else buf.append('\n')
                buf.append(str)
            }
            return buf.toString()
        } catch (e: IOException) {
            Log.e("HomeActivity", "Error opening asset $name")
        } finally {
            if (`in` != null) {
                try {
                    `in`.close()
                } catch (e: IOException) {
                    Log.e("HomeActivity", "Error closing asset $name")
                }
            }
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        FolioReader.clear()
    }

    override fun onHighlight(highlight: HighLight, type: HighLightAction) {
        Toast.makeText(
            this,
            "highlight id = " + highlight.uuid + " type = " + type,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onFolioReaderClosed() {
        Log.v(LOG_TAG, "-> onFolioReaderClosed")
    }

    companion object {
        private val LOG_TAG = HomeActivity::class.java.simpleName
    }
}