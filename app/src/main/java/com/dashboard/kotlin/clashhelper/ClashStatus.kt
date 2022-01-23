package com.dashboard.kotlin.clashhelper

import android.util.Log
import com.dashboard.kotlin.GExternalCacheDir
import com.dashboard.kotlin.KV
import java.net.HttpURLConnection
import java.net.URL
import com.dashboard.kotlin.suihelper.suihelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class ClashStatus {
    var trafficThreadFlag: Boolean = true
    var trafficRawText: String = "{\"up\":\"0\",\"down\":\"0\"}"

    fun runStatus(): Boolean {
        var isRunning = false
        thread(start = true) {
            isRunning = try {
                val conn =
                    URL(ClashConfig.baseURL).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer ${ClashConfig.clashSecret}")
                conn.inputStream.bufferedReader().readText() == "{\"hello\":\"clash\"}\n"
            } catch (ex: Exception) {
                false
            }
        }.join()
        return isRunning
    }

    fun getTraffic() {
        trafficThreadFlag = true
        val secret = ClashConfig.clashSecret
        val baseURL = ClashConfig.baseURL
        Thread {
            try {
                val conn =
                    URL("${baseURL}/traffic").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $secret")
                conn.inputStream.use {
                    while (trafficThreadFlag) {
                        trafficRawText = it.bufferedReader().readLine()
                        Log.d("TRAFFIC", trafficRawText)
                    }
                }
            } catch (ex: Exception) {
                Log.w("W", ex.toString())
            }
        }.start()
    }

    fun stopGetTraffic() {
        trafficThreadFlag = false
    }


}

object ClashConfig {

    var corePath: String
        get() = KV.decodeString("core_path")?:"/system/bin/clash"
        set(value) {
            KV.encode("core_path", value)
        }

    var scriptsPath: String
        get() = KV.decodeString("scripts_path")?:"/data/clash/scripts"
        set(value) {
            if (value.last() == '/')
                KV.encode("scripts_path",value.substring(0, value.length-1))
            else
                KV.encode("scripts_path", value)
        }

    val clashPath: String
        get() = "/data/clash"

    val baseURL: String
        get() {
            return "http://${getExternalController()}"
        }
    val clashSecret: String
        get() {
            return getSecret()
        }

    var clashDashBoard: String
        get() {
            return setFile(
                clashPath, "template"
            ) { getFromFile("${GExternalCacheDir}/template", "external-ui") }
        }
        set(value) {
            setFileNR(
                clashPath, "template"
            ) { modifyFile("${GExternalCacheDir}/template", "external-ui", value) }
            return
        }

    fun updateConfig(type: String) {
        when (type) {
            "CFM" -> {
                mergeConfig("${clashPath}/run/config.yaml")
                updateConfigNet("${clashPath}/run/config.yaml")
            }
            "CPFM" -> {
                mergeConfig("${clashPath}/config_new.yaml")
                suihelper.suCmd("mv '${clashPath}/config_new.yaml' '${clashPath}/config.yaml'")
                updateConfigNet("${clashPath}/config.yaml")
            }
        }
    }


    private fun updateConfigNet(configPath: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val conn =
                    URL("${baseURL}/configs?force=false").openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.setRequestProperty("Authorization", "Bearer $clashSecret")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                conn.outputStream.use { os ->
                    os.write(
                        JSONObject(
                            mapOf(
                                "path" to configPath
                            )
                        ).toString().toByteArray()
                    )
                }

                conn.connect()
                Log.i("NET", "HTTP CODE : ${conn.responseCode}")
                conn.inputStream.use {
                    val data = it.bufferedReader().readText()
                    Log.i("NET", data)
                }


            } catch (ex: Exception) {
                Log.w("NET", ex.toString())
            }
        }


    }

    private fun mergeConfig(outputFilePath: String) {
        setFileNR(clashPath, "config.yaml") {
            setFileNR(clashPath, "template") {
                mergeFile(
                    "${GExternalCacheDir}/config.yaml",
                    "${GExternalCacheDir}/template",
                    "${GExternalCacheDir}/config_output.yaml"
                )
                suihelper.suCmd("mv '${GExternalCacheDir}/config_output.yaml' '$outputFilePath'")
            }
        }
    }

    private fun getSecret(): String {
        return setFile(
            clashPath, "template"
        ) { getFromFile("${GExternalCacheDir}/template", "secret") }
    }


    private fun getExternalController(): String {

        val temp = setFile(
            clashPath, "template"
        ) { getFromFile("${GExternalCacheDir}/template", "external-controller") }

        if (temp.startsWith(":")) {
            return "127.0.0.1$temp"
        } else {
            return temp
        }

    }

    private fun setFile(dirPath: String, fileName: String, func: () -> String): String {
        copyFile(dirPath, fileName)
        val temp = func()
        deleteFile(GExternalCacheDir, fileName)
        return temp
    }

    private fun setFileNR(dirPath: String, fileName: String, func: () -> Unit) {
        copyFile(dirPath, fileName)
        func()
        suihelper.suCmd("cp '${GExternalCacheDir}/${fileName}' '${dirPath}/${fileName}'")
        deleteFile(GExternalCacheDir, fileName)
    }

    private fun copyFile(dirPath: String, fileName: String) {
        suihelper.suCmd("cp '${dirPath}/${fileName}' '${GExternalCacheDir}/${fileName}'")
        suihelper.suCmd("chmod +rw '${GExternalCacheDir}/${fileName}'")
        return
    }

    private fun deleteFile(dirPath: String, fileName: String) {
        try {
            File(dirPath, fileName).delete()
        } catch (ex: Exception) {
        }
    }

    private external fun getFromFile(path: String, node: String): String
    private external fun modifyFile(path: String, node: String, value: String)
    private external fun mergeFile(
        mainFilePath: String,
        templatePath: String,
        outputFilePath: String
    )


    init {
        System.loadLibrary("yaml-reader")
    }

}