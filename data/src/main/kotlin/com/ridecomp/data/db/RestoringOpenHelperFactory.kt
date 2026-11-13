package com.ridecomp.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.ridecomp.data.backup.PendingRestore

/**
 * Applies a restore staged by the previous run (12-FEATURE-SPECS §12.5, D-39) right before the database
 * file is first opened. Room opens it lazily on its own background executor, so the file checks and the
 * rename never run on the main thread (STB-01), and still happen before anything reads the data.
 */
internal class RestoringOpenHelperFactory(
    private val context: Context,
    private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        RestoringOpenHelper(context.applicationContext, delegate.create(configuration))

    private class RestoringOpenHelper(private val context: Context, private val helper: SupportSQLiteOpenHelper) :
        SupportSQLiteOpenHelper by helper {
        private var isRestoreChecked = false

        override val writableDatabase: SupportSQLiteDatabase
            get() {
                applyRestoreOnce()
                return helper.writableDatabase
            }

        override val readableDatabase: SupportSQLiteDatabase
            get() {
                applyRestoreOnce()
                return helper.readableDatabase
            }

        @Synchronized
        private fun applyRestoreOnce() {
            if (isRestoreChecked) return
            PendingRestore.applyIfPresent(context)
            isRestoreChecked = true
        }
    }
}
