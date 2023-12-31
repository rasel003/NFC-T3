package com.sajjad.nfct3

import android.app.Application
import android.content.ContentValues
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.*
import java.io.UnsupportedEncodingException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.experimental.and

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private val TAG = MainViewModel::class.java.simpleName
        private const val prefix = "android.nfc.tech."
    }

    private val liveNFC: MutableStateFlow<NFCStatus?>
    private val liveToast: MutableSharedFlow<String?>
    private val liveTagData: MutableStateFlow<String?>

    init {
        Log.d(TAG, "constructor")
        liveNFC = MutableStateFlow(null)
        liveToast = MutableSharedFlow()
        liveTagData = MutableStateFlow(null)
    }

    //region Toast Methods
    private fun updateToast(message: String) {
        Coroutines.io(this@MainViewModel) {
            liveToast.emit(message)
        }
    }

    private suspend fun postToast(message: String) {
        Log.d(TAG, "postToast(${message})")
        liveToast.emit(message)
    }

    public fun observeToast(): SharedFlow<String?> {
        return liveToast.asSharedFlow()
    }

    //endregion
    public fun getNFCFlags(): Int {
        return NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE //or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
    }

    fun getExtras(): Bundle {
        val options = Bundle();
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 30000);
        return options
    }

    //region NFC Methods
    fun enableOrDisableNFCDataRead(isEnable: Boolean) {
        Coroutines.io(this@MainViewModel) {
            Log.d(TAG, "onCheckNFC(${isEnable})")
            if (isEnable) {
                postNFCStatus(NFCStatus.Tap)
            } else {
                postNFCStatus(NFCStatus.NoOperation)
                postToast("NFC is Disabled, Please Toggle On!")
            }
        }
    }

    fun readTag(tag: Tag?) {
        Coroutines.default(this@MainViewModel) {
            Log.d(TAG, "readTag(${tag} ${tag?.techList})")
            postNFCStatus(NFCStatus.Process)
            val stringBuilder: StringBuilder = StringBuilder()
            val id: ByteArray? = tag?.id
            stringBuilder.append("Tag ID (hex): ${getHex(id!!)} \n")
            stringBuilder.append("Tag ID (dec): ${getDec(id)} \n")
            stringBuilder.append("Tag ID (reversed): ${getReversed(id)} \n")
            stringBuilder.append("Technologies: ")

            tag.techList.forEach { tech ->
                stringBuilder.append(tech.substring(prefix.length))
                stringBuilder.append(", ")
            }
            stringBuilder.delete(stringBuilder.length - 2, stringBuilder.length)
            tag.techList.forEach { tech ->
                if (tech.equals(MifareClassic::class.java.name)) {
                    stringBuilder.append('\n')
                    val mifareTag: MifareClassic = MifareClassic.get(tag)
                    val type: String = when (mifareTag.type) {
                        MifareClassic.TYPE_CLASSIC -> "Classic"
                        MifareClassic.TYPE_PLUS -> "Plus"
                        MifareClassic.TYPE_PRO -> "Pro"
                        else -> "Unknown"
                    }
                    stringBuilder.append("Mifare Classic type: $type \n")
                    stringBuilder.append("Mifare size: ${mifareTag.size} bytes \n")
                    stringBuilder.append("Mifare sectors: ${mifareTag.sectorCount} \n")
                    stringBuilder.append("Mifare blocks: ${mifareTag.blockCount}")
                }
                if (tech.equals(MifareUltralight::class.java.name)) {
                    stringBuilder.append('\n');
                    val mifareUlTag: MifareUltralight = MifareUltralight.get(tag);
                    val type: String = when (mifareUlTag.type) {
                        MifareUltralight.TYPE_ULTRALIGHT -> "Ultralight"
                        MifareUltralight.TYPE_ULTRALIGHT_C -> "Ultralight C"
                        else -> "Unkown"
                    }
                    stringBuilder.append("Mifare Ultralight type: ");
                    stringBuilder.append(type)
                }
            }
            Log.d(TAG, "Datum: $stringBuilder")
            Log.d(ContentValues.TAG, "dumpTagData Return \n $stringBuilder")
            postNFCStatus(NFCStatus.Read)
            liveTagData.emit("${getDateTimeNow()} \n $stringBuilder")
        }
    }
    fun readTagNdefData(tag: Tag?) {
        Coroutines.default(this@MainViewModel) {
            val ndef = Ndef.get(tag)
            if(ndef == null){
                liveTagData.emit( "NDEF is not supported by this Tag")
            }else {
                val ndefMessage = ndef.cachedNdefMessage
                val records = ndefMessage.records
                for (ndefRecord in records) {
                    if (ndefRecord.tnf == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(
                            ndefRecord.type,
                            NdefRecord.RTD_TEXT
                        )
                    ) {
                        try {
                             readText(ndefRecord)
                        } catch (e: UnsupportedEncodingException) {
                            Log.e(TAG, "Unsupported Encoding", e)
                            liveTagData.emit("Unsupported Encoding")
                        }
                    }
                }
            }
        }
    }

    @Throws(UnsupportedEncodingException::class)
    private suspend fun readText(record: NdefRecord) {
        /*
         * See NFC forum specification for "Text Record Type Definition" at 3.2.1
         *
         * http://www.nfc-forum.org/specs/
         *
         * bit_7 defines encoding
         * bit_6 reserved for future use, must be 0
         * bit_5..0 length of IANA language code
         */
        val payload = record.payload
        // Get the Text Encoding
        val textEncoding = if (payload[0].toInt() and 128 == 0) "UTF-8" else "UTF-16"
        // Get the Language Code
        val languageCodeLength = payload[0].toInt() and 51

        // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
        // e.g. "en"

        // Get the Text
        liveTagData.emit( Common.getText(payload, languageCodeLength, textEncoding));
    }

    public fun updateNFCStatus(status: NFCStatus) {
        Coroutines.io(this@MainViewModel) {
            postNFCStatus(status)
        }
    }

    private suspend fun postNFCStatus(status: NFCStatus) {
        Log.d(TAG, "postNFCStatus(${status})")
        if (NFCManager.isSupportedAndEnabled(getApplication())) {
            liveNFC.emit(status)
        } else if (NFCManager.isNotEnabled(getApplication())) {
            liveNFC.emit(NFCStatus.NotEnabled)
            postToast("Please Enable your NFC!")
        } else if (NFCManager.isNotSupported(getApplication())) {
            liveNFC.emit(NFCStatus.NotSupported)
            postToast("NFC Not Supported!")
        }
        /*if (NFCManager.isSupportedAndEnabled(getApplication()) && status == NFCStatus.Tap) {
            postToast("Please Tap Now!")
        } else {
            postToast("Error")

        }*/
    }

    public fun observeNFCStatus(): StateFlow<NFCStatus?> {
        return liveNFC.asStateFlow()
    }

    //endregion
    //region Tags Information Methods
    private fun getDateTimeNow(): String {
        Log.d(TAG, "getDateTimeNow()")
        val TIME_FORMAT: DateFormat = SimpleDateFormat.getDateTimeInstance()
        val now: Date = Date()
        Log.d(ContentValues.TAG, "getDateTimeNow() Return ${TIME_FORMAT.format(now)}")
        return TIME_FORMAT.format(now)
    }

    private fun getHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (i in bytes.indices.reversed()) {
            val b: Int = bytes[i].and(0xff.toByte()).toInt()
            if (b < 0x10) sb.append('0')
            sb.append(Integer.toHexString(b))
            if (i > 0)
                sb.append(" ")
        }
        return sb.toString()
    }

    private fun getDec(bytes: ByteArray): Long {
        Log.d(TAG, "getDec()")
        var result: Long = 0
        var factor: Long = 1
        for (i in bytes.indices) {
            val value: Long = bytes[i].and(0xffL.toByte()).toLong()
            result += value * factor
            factor *= 256L
        }
        return result
    }

    private fun getReversed(bytes: ByteArray): Long {
        Log.d(TAG, "getReversed()")
        var result: Long = 0
        var factor: Long = 1
        for (i in bytes.indices.reversed()) {
            val value = bytes[i].and(0xffL.toByte()).toLong()
            result += value * factor
            factor *= 256L
        }
        return result
    }
    fun observeTagData(): StateFlow<String?> {
        return liveTagData.asStateFlow()
    }
    //endregion
}