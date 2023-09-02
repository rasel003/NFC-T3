package com.sajjad.nfct3

import android.content.Intent
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.sajjad.nfct3.databinding.ActivityBinder
import com.sajjad.nfct3.preference.AppPrefsManager
import java.io.UnsupportedEncodingException
import java.util.Arrays
import kotlin.math.abs

class ResultActivity : AppCompatActivity() {

    private lateinit var binder: ActivityBinder
    private lateinit var prefsManager: AppPrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        binder = DataBindingUtil.setContentView(this@ResultActivity, R.layout.activity_result)
        binder.lifecycleOwner = this@ResultActivity
        super.onCreate(savedInstanceState)

        prefsManager = AppPrefsManager(this)
        binder.tvBufferValue.text = prefsManager.bufferData
        binder.tvAnalyteValue.text = prefsManager.analyteData

        binder.btnClearData.setOnClickListener {
            prefsManager.clearSession()
            binder.tvBufferValue.text = getString(R.string.explanation)
            binder.tvAnalyteValue.text = getString(R.string.explanation)
            binder.tvMessage.visibility = View.GONE
        }

        binder.btnBuffer.setOnClickListener { getDataFromNFCTag(DataType.Buffer) }
        binder.btnAnalyte.setOnClickListener { getDataFromNFCTag(DataType.Analyte) }
    }

    private fun calculateResult() {
        val bufferValue = binder.tvBufferValue.text.toString().toFloatOrNull()
        val analyteValue = binder.tvAnalyteValue.text.toString().toFloatOrNull()

        if (bufferValue != null && analyteValue != null) {
            val result = abs(bufferValue - analyteValue)
            binder.tvMessage.visibility = View.VISIBLE
            if (result > 5) {
                binder.tvMessage.text = "Result : Positive"
            } else {
                binder.tvMessage.text = "Result : Negative"

            }
        } else {
            binder.tvMessage.visibility = View.GONE
        }
    }

    private fun getDataFromNFCTag(type: DataType) {
        when (type) {
            DataType.Buffer -> {
                mStartForResultBuffer.launch(MainActivity.getCallingIntent(this))
            }

            DataType.Analyte -> {
                mStartForResultAnalyte.launch(MainActivity.getCallingIntent(this))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        /**
         * This method gets called, when a new Intent gets associated with the current activity instance.
         * Instead of creating a new activity, onNewIntent will be called. For more information have a look
         * at the documentation.
         *
         * In our case this method gets called, when the user attaches a Tag to the device.
         */
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        Toast.makeText(this, "" + intent.action, Toast.LENGTH_SHORT).show()
        val action = intent.action
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
            val type = intent.type
            if (MainActivity.MIME_TEXT_PLAIN == type) {
                val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
                readTagNdefData(tag)
            } else {
                Toast.makeText(this, "Wrong mime type: $type", Toast.LENGTH_SHORT).show()
            }
        } else if (NfcAdapter.ACTION_TECH_DISCOVERED == action) {

            // In case we would still use the Tech Discovered Intent
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            val techList = tag!!.techList
            val searchedTech = Ndef::class.java.name
            for (tech in techList) {
                if (searchedTech == tech) {
                    readTagNdefData(tag)
                    break
                }
            }
        }
    }

    private fun readTagNdefData(tag: Tag?) {

        val ndef = Ndef.get(tag)
        if (ndef == null) {
            binder.tvBufferValue.text = "NDEF is not supported by this Tag"
        } else {
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
                        binder.tvBufferValue.text = "Unsupported Encoding"
                    }
                }
            }
        }
    }

    @Throws(UnsupportedEncodingException::class)
    private fun readText(record: NdefRecord) {
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
        binder.tvBufferValue.text = Common.getText(payload, languageCodeLength, textEncoding)
    }

    private var mStartForResultBuffer = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = result.data
            intent?.getStringExtra(Common.result)?.let {
                binder.tvBufferValue.text = it
                prefsManager.bufferData = it
                calculateResult()
            }

        }
    }
    private var mStartForResultAnalyte = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = result.data
            intent?.getStringExtra(Common.result)?.let {
                binder.tvAnalyteValue.text = it
                prefsManager.analyteData = it
                calculateResult()
            }
        }
    }

    companion object {
        private val TAG = ResultActivity::class.java.simpleName
    }
}