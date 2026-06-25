package com.mobenchant.mvn123123123123123

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.io.FileReader
import java.io.FileWriter

object MobEnchantConfig {
    var debugEnabled: Boolean = false

    private val configFile: File = FabricLoader.getInstance().configDir.resolve("mob_enchant.json").toFile()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun load() {
        if (configFile.exists()) {
            try {
                FileReader(configFile).use { reader ->
                    val data = gson.fromJson(reader, ConfigData::class.java)
                    if (data != null) {
                        debugEnabled = data.debugEnabled
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            save()
        }
    }

    fun save() {
        try {
            val data = ConfigData(debugEnabled)
            FileWriter(configFile).use { writer ->
                gson.toJson(data, writer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class ConfigData(var debugEnabled: Boolean = false)
}
