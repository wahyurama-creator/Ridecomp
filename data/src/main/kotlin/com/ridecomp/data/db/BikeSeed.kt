package com.ridecomp.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.ridecomp.core.config.ProfileDefaults
import com.ridecomp.core.model.ActivityKind
import com.ridecomp.data.mapper.SettingsKeys
import com.ridecomp.data.mapper.toDbValue

/**
 * Creates the first bike (D-89), so a ride always has one. In the 7→8 migration it takes the bike weight
 * and wheel size the rider already entered in the profile and the last used activity profile as its type;
 * on a new install the defaults and Road. Its name stays blank and is shown as a localized "My bike".
 * Plain SQL with bind arguments.
 */
internal object BikeSeed {
    private const val PROFILES = "`${DbNames.TABLE_ACTIVITY_PROFILES}`"
    private const val PROFILE = "`${DbNames.TABLE_PROFILE}`"
    private const val SETTINGS = "`${DbNames.TABLE_SETTINGS}`"

    private const val INSERT_SQL = "INSERT INTO `${DbNames.TABLE_BIKES}` " +
        "(`name`, `profile_id`, `weight_kg`, `wheel_circumference_mm`) SELECT '', " +
        // Type: the last used profile if it still exists, else Road, else any profile.
        "COALESCE(" +
        "(SELECT `id` FROM $PROFILES WHERE `id` = " +
        "(SELECT CAST(`value` AS INTEGER) FROM $SETTINGS WHERE `key` = ?)), " +
        "(SELECT `id` FROM $PROFILES WHERE `kind` = ? ORDER BY `sort_order`, `id` LIMIT 1), " +
        "(SELECT MIN(`id`) FROM $PROFILES), 0), " +
        "COALESCE((SELECT `bike_weight_kg` FROM $PROFILE WHERE `id` = ?), ?), " +
        "COALESCE((SELECT `wheel_circumference_mm` FROM $PROFILE WHERE `id` = ?), ?)"

    fun insertFirstBike(db: SupportSQLiteDatabase) {
        db.execSQL(
            INSERT_SQL,
            arrayOf<Any?>(
                SettingsKeys.LAST_PROFILE_ID,
                ActivityKind.ROAD.toDbValue(),
                DbNames.SINGLE_PROFILE_ID,
                ProfileDefaults.BIKE_WEIGHT_KG,
                DbNames.SINGLE_PROFILE_ID,
                ProfileDefaults.WHEEL_CIRCUMFERENCE_MM,
            ),
        )
    }
}
