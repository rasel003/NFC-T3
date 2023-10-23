package com.sajjad.nfct3

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.sajjad.nfct3.databinding.ActivityMainBinder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.UnsupportedEncodingException
import java.util.Arrays


/**
 * Activity for reading data from an NDEF Tag.
 *
 * @author Ralf Wondratschek
 */
class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private lateinit var binder: ActivityMainBinder
    private val viewModel: MainViewModel by lazy { ViewModelProvider(this)[MainViewModel::class.java] }

    private var mNfcAdapter: NfcAdapter? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        binder = DataBindingUtil.setContentView(this@MainActivity, R.layout.activity_main)
        binder.viewModel = viewModel
        binder.lifecycleOwner = this@MainActivity
        super.onCreate(savedInstanceState)

        val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        this.registerReceiver(mReceiver, filter)

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (mNfcAdapter == null) {
            // Stop here, we definitely need NFC
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show()
            finish()
            return
        } else if (mNfcAdapter?.isEnabled == true) {
            binder.tvMessage.text = getString(R.string.please_tap_now)
            viewModel.enableOrDisableNFCDataRead(true)
        } else {
            binder.tvMessage.setText(R.string.nfc_disabled)
        }

        Coroutines.main(this@MainActivity) { scope ->
            scope.launch(block = {
                binder.viewModel?.observeNFCStatus()?.collectLatest(action = { status ->
                    Log.d(TAG, "observeNFCStatus $status")
                    if (status == NFCStatus.NoOperation) NFCManager.disableReaderMode(
                        this@MainActivity,
                        this@MainActivity
                    )
                    else if (status == NFCStatus.Tap) NFCManager.enableReaderMode(
                        this@MainActivity,
                        this@MainActivity,
                        this@MainActivity,
                        viewModel.getNFCFlags(),
                        viewModel.getExtras()
                    )
                })
            })
            scope.launch(block = {
                binder.viewModel?.observeToast()?.collectLatest(action = { message ->
                    Log.d(TAG, "observeToast $message")
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                })
            })
            scope.launch(block = {
                binder.viewModel?.observeTagData()?.collectLatest(action = { tag ->
                    Log.d(TAG, "observeTag $tag")
                    tag?.let {

                        //checking if this activity is called for result, if not @null that means this activity is called for result
                        if (callingActivity != null) {
                            val returnIntent = intent
                            returnIntent.putExtra(Common.result, it)
                            setResult(RESULT_OK, returnIntent)
                            finish()
                        } else ResultActivity.start(this@MainActivity, it)
                    }
                })
            })
        }
    }


    override fun onTagDiscovered(tag: Tag?) {
//        binder.viewModel?.readTag(tag)
        binder.viewModel?.readTagNdefData(tag)
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

        retrieveNFCMessage(intent).let {
            Toast.makeText(this, "Ndef : $it", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleIntent(intent: Intent) {
        Toast.makeText(this, "" + intent.action, Toast.LENGTH_SHORT).show()
        val action = intent.action
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
            val type = intent.type
            if (MIME_TEXT_PLAIN == type) {
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
            Toast.makeText(this, "NDEF is not supported by this Tag", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this, "Unsupported Encoding", Toast.LENGTH_SHORT).show()
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

        ResultActivity.start(this@MainActivity, Common.getText(payload, languageCodeLength, textEncoding))
    }

    fun retrieveNFCMessage(intent: Intent?): String {
        intent?.let {
            if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
                val nDefMessages = getNDefMessages(intent)
                nDefMessages[0].records?.let {
                    it.forEach {
                        it?.payload.let {
                            it?.let {
                                return String(it)

                            }
                        }
                    }
                }

            } else {
                return "Touch NFC tag to read data"
            }
        }
        return "Touch NFC tag to read data"
    }

    private fun getNDefMessages(intent: Intent): Array<NdefMessage> {

        val rawMessage = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        rawMessage?.let {
            return rawMessage.map {
                it as NdefMessage
            }.toTypedArray()
        }
        // Unknown tag type
        val empty = byteArrayOf()
        val record = NdefRecord(NdefRecord.TNF_UNKNOWN, empty, empty, empty)
        val msg = NdefMessage(arrayOf(record))
        return arrayOf(msg)
    }

    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) {
                val state = intent.getIntExtra(
                    NfcAdapter.EXTRA_ADAPTER_STATE,
                    NfcAdapter.STATE_OFF
                )
                when (state) {
                    NfcAdapter.STATE_OFF -> {
                        binder.tvMessage.text = getString(R.string.nfc_disabled)
                        viewModel.enableOrDisableNFCDataRead(false)
                    }

                    NfcAdapter.STATE_TURNING_OFF -> {}
                    NfcAdapter.STATE_ON -> {
                        binder.tvMessage.text = getText(R.string.please_tap_now)
                        viewModel.enableOrDisableNFCDataRead(true)
                    }

                    NfcAdapter.STATE_TURNING_ON -> {}
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        viewModel.enableOrDisableNFCDataRead(false)

        // Remove the broadcast listener
        unregisterReceiver(mReceiver)
    }

    companion object {
        const val MIME_TEXT_PLAIN = "text/plain"
        private val TAG = MainActivity::class.java.simpleName

        @JvmStatic
        fun getCallingIntent(activity: Activity): Intent {
            return Intent(activity, MainActivity::class.java)
        }
    }
}