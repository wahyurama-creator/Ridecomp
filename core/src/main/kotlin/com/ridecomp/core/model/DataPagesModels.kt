package com.ridecomp.core.model

/** Limits of the data page editor (01-PRD F-04, 02-DESIGN §3.4). */
public object DataPageLimits {
    public const val MAX_FIELDS: Int = 8

    /** Every count from 1 to [MAX_FIELDS]: a full-width hero counts as a field (D-54, e.g. 7 = hero + 6). */
    public val FIELD_COUNTS: List<Int> = (1..MAX_FIELDS).toList()
    public const val MAX_PAGES: Int = 6
}

/**
 * One data page: metric keys (stable ids such as `speed`, `hr`, stored as JSON, 05-DATA-MODEL §2) and
 * whether the first field gets its own full-width row.
 */
public data class DataPageLayout(val fields: List<String>, val isFirstFullWidth: Boolean = false)

/** The rider's data pages, in swipe order. */
public data class DataPagesLayout(val pages: List<DataPageLayout>)

/**
 * Makes stored or edited pages safe to render (STB-09): unknown metric keys are dropped (e.g. a metric
 * removed in an update), each page keeps at most [DataPageLimits.MAX_FIELDS] fields, empty pages are
 * removed and at most [DataPageLimits.MAX_PAGES] pages remain. Returns null when nothing usable is left,
 * meaning "use the defaults".
 */
public fun DataPagesLayout.sanitized(knownKeys: Set<String>): DataPagesLayout? {
    val pages = pages
        .map { page -> page.copy(fields = page.fields.filter { it in knownKeys }.take(DataPageLimits.MAX_FIELDS)) }
        .filter { it.fields.isNotEmpty() }
        .take(DataPageLimits.MAX_PAGES)
    return pages.takeIf { it.isNotEmpty() }?.let(::DataPagesLayout)
}

/**
 * Changes the number of fields of a page: extra fields are cut, new ones are filled with [filler] keys
 * not already on the page (in order), so the rider only has to adjust them.
 */
public fun DataPageLayout.withFieldCount(count: Int, filler: List<String>): DataPageLayout {
    val target = count.coerceIn(1, DataPageLimits.MAX_FIELDS)
    if (target <= fields.size) return copy(fields = fields.take(target))
    val extra = filler.filterNot { it in fields }.take(target - fields.size)
    return copy(fields = fields + extra)
}
