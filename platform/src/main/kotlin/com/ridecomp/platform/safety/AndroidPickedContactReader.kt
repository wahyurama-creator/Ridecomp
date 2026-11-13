package com.ridecomp.platform.safety

import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.net.toUri
import com.ridecomp.core.source.DispatcherProvider
import com.ridecomp.core.source.PickedContact
import com.ridecomp.core.source.PickedContactReader
import kotlinx.coroutines.withContext

/**
 * Reads the single contact returned by `ACTION_PICK` on phone numbers. The picker grants temporary access
 * to that one row, so READ_CONTACTS is not needed (SEC-SMS-05). Only URIs of the contacts provider are
 * accepted (SEC-IN-01).
 */
class AndroidPickedContactReader(private val context: Context, private val dispatchers: DispatcherProvider) :
    PickedContactReader {
    override suspend fun read(uri: String): PickedContact? = withContext(dispatchers.io) {
        val parsed = uri.toUri()
        if (parsed.authority != ContactsContract.AUTHORITY) return@withContext null
        context.contentResolver.query(parsed, PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val number = cursor.getString(cursor.getColumnIndexOrThrow(Phone.NUMBER)) ?: return@use null
            val name = cursor.getString(cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)).orEmpty()
            PickedContact(name, number)
        }
    }

    private companion object {
        val PROJECTION = arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER)
    }
}
