package com.sajjad.nfct3.preference

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

class AppPrefsManager @SuppressLint("CommitPrefEdits") constructor(mContext: Context) {
    private var preferences: SharedPreferences = mContext.getSharedPreferences(PREF_NAME, 0)
    private var editor: SharedPreferences.Editor = preferences.edit()

    var bufferData: String?
        get() = preferences.getString(USER_NAME, "")
        set(userName) {
            editor.putString(USER_NAME, userName)
            editor.apply()
        }

    var analyteData: String?
        get() = preferences.getString(KEY_PASSWORD, "")
        set(password) {
            editor.putString(KEY_PASSWORD, password)
            editor.apply()
        }


    fun clearSession() {
        editor.clear()
        editor.apply()
    }


    companion object {
        private const val PREF_NAME = "sajjad_pref"

        private const val USER_NAME = "user_name"
        private const val KEY_PASSWORD = "user_password"


    }

}