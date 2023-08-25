package com.sajjad.nfct3

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log

public object NFCManager {

    private val TAG = NFCManager::class.java.simpleName

    public fun enableReaderMode(context : Context, activity : Activity, callback : NfcAdapter.ReaderCallback, flags : Int, extras : Bundle) {
        try {
            NfcAdapter.getDefaultAdapter(context).enableReaderMode(activity, callback, flags, extras)
        } catch (ex : UnsupportedOperationException) { Log.e(TAG,"UnsupportedOperationException ${ex.message}", ex)

        }
    }

    public fun disableReaderMode(context : Context, activity : Activity) {
        try {
            NfcAdapter.getDefaultAdapter(context).disableReaderMode(activity)
        } catch (ex : UnsupportedOperationException) { Log.e(TAG,"UnsupportedOperationException ${ex.message}", ex)

        }
    }

    public fun isSupported(context : Context) : Boolean {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        return if (nfcAdapter == null) false
        else true
    }

    public fun isNotSupported(context : Context) : Boolean {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        return if (nfcAdapter == null) true
        else false
    }

    public fun isEnabled(context : Context) : Boolean {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        return if (nfcAdapter == null) false
        else nfcAdapter.isEnabled()
    }

    public fun isNotEnabled(context : Context) : Boolean {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        return if (nfcAdapter == null) true
        else nfcAdapter.isEnabled().not()
    }

    public fun isSupportedAndEnabled(context : Context) : Boolean {
        return isSupported(context) && isEnabled(context)
    }
}