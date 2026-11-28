package io.unthrottled.doki.service

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.Urls
import com.intellij.util.io.HttpRequests
import io.unthrottled.doki.util.Logging
import io.unthrottled.doki.util.logger
import io.unthrottled.doki.util.toOptional

const val DOKI_ICONS_PLUGIN_ID = "io.unthrottled.doki.icons"

object PluginService : Logging {
  private val gson by lazy { Gson() }

  fun areIconsInstalled(): Boolean =
    PluginManagerCore.isPluginInstalled(
      PluginId.getId(DOKI_ICONS_PLUGIN_ID),
    )

  private val PLUGIN_MANAGER_URL by lazy {
    ApplicationInfo.getInstance()
      .toOptional()
      .filter { it is ApplicationInfoEx }
      .map { it as ApplicationInfoEx }
      .map { it.pluginManagerUrl }
      .orElseGet { "https://plugins.jetbrains.com" }
      .trimEnd('/')
  }
  private val COMPATIBLE_UPDATE_URL by lazy { "$PLUGIN_MANAGER_URL/api/search/compatibleUpdates" }

  private data class CompatibleUpdateRequest(
    val build: String,
    val pluginXMLIds: List<String>,
  )

  /**
   * Object from Search Service for getting compatible updates for IDE.
   * [externalUpdateId] update ID from Plugin Repository database.
   * [externalPluginId] plugin ID from Plugin Repository database.
   */
  data class IdeCompatibleUpdate(
    @SerializedName("id")
    val externalUpdateId: String = "",
    @SerializedName("pluginId")
    val externalPluginId: String = "",
    @SerializedName("pluginXmlId")
    val pluginId: String = "",
    val version: String = "",
  )

  private fun getLastCompatiblePluginUpdate(ids: Set<PluginId>): List<IdeCompatibleUpdate> {
    return try {
      if (ids.isEmpty()) {
        return emptyList()
      }

      val data =
        gson.toJson(
          CompatibleUpdateRequest(
            ApplicationInfo.getInstance()
              .build.asString(),
            ids.map { it.idString },
          ),
        )
      HttpRequests
        .post(Urls.newFromEncoded(COMPATIBLE_UPDATE_URL).toExternalForm(), HttpRequests.JSON_CONTENT_TYPE)
        .productNameAsUserAgent()
        .throwStatusCodeException(false)
        .connect {
          it.write(data)
          gson.fromJson<List<IdeCompatibleUpdate>>(
            it.reader,
            object : TypeToken<List<IdeCompatibleUpdate>>() {}.type,
          )
        }
    } catch (e: Exception) {
      logger().warn("Unable to check to see if plugin $ids is compatible for reasons", e)
      emptyList()
    }
  }
}
