package com.ridecomp.data.json

import com.ridecomp.core.model.DataPageLayout
import com.ridecomp.core.model.DataPagesLayout
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Data page configuration as JSON in the `settings` table (05-DATA-MODEL §2):
 * `{"pages":[{"fields":["speed","distance"],"first_full_width":true}]}`.
 * Decoding never throws: damaged JSON means "use the defaults" (STB-09). Keys are validated later by
 * `sanitized()` against the metrics the app knows.
 */
internal object DataPagesJson {
    private const val PAGES = "pages"
    private const val FIELDS = "fields"
    private const val FIRST_FULL_WIDTH = "first_full_width"

    fun encode(layout: DataPagesLayout): String {
        val pages = JSONArray()
        layout.pages.forEach { page ->
            pages.put(
                JSONObject()
                    .put(FIELDS, JSONArray(page.fields))
                    .put(FIRST_FULL_WIDTH, page.isFirstFullWidth),
            )
        }
        return JSONObject().put(PAGES, pages).toString()
    }

    fun decode(json: String): DataPagesLayout? = try {
        val pages = JSONObject(json).getJSONArray(PAGES)
        DataPagesLayout(
            List(pages.length()) { index ->
                val page = pages.getJSONObject(index)
                val fields = page.getJSONArray(FIELDS)
                DataPageLayout(
                    fields = List(fields.length()) { fields.getString(it) },
                    isFirstFullWidth = page.optBoolean(FIRST_FULL_WIDTH, false),
                )
            },
        )
    } catch (_: JSONException) {
        null
    }
}
