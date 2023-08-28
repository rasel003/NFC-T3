package com.sajjad.nfct3

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentFilter.MalformedMimeTypeException
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.sajjad.nfct3.databinding.ActivityBinder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.UnsupportedEncodingException
import java.util.Arrays

/**
 * Activity for reading data from an NDEF Tag.
 *
 * @author Ralf Wondratschek
 */
class MainActivity : AppCompatActivity(), CompoundButton.OnCheckedChangeListener, NfcAdapter.ReaderCallback {

    private lateinit var binder : ActivityBinder
    private val viewModel : MainViewModel by lazy { ViewModelProvider(this)[MainViewModel::class.java] }

    private var mNfcAdapter: NfcAdapter? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        binder = DataBindingUtil.setContentView(this@MainActivity, R.layout.activity_main)
        binder.viewModel = viewModel
        binder.lifecycleOwner = this@MainActivity
        super.onCreate(savedInstanceState)

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (mNfcAdapter == null) {
            // Stop here, we definitely need NFC
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!mNfcAdapter!!.isEnabled) {
            binder.textViewExplanation.text = "NFC is disabled."
        } else {
            binder.textViewExplanation.setText(R.string.explanation)
        }
        handleIntent(intent)



        binder.toggleButton.setOnCheckedChangeListener(this@MainActivity)

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
                binder.viewModel?.observeTag()?.collectLatest(action = { tag ->
                    Log.d(TAG, "observeTag $tag")
                    binder.tvTagInfo.text = tag
                })
            })
        }
    }

    override fun onResume() {
        super.onResume()
        /**
         * It's important, that the activity is in the foreground (resumed). Otherwise
         * an IllegalStateException is thrown.
         */
        setupForegroundDispatch(this, mNfcAdapter)
    }

    override fun onPause() {
        /**
         * Call this before onPause, otherwise an IllegalArgumentException is thrown as well.
         */
        stopForegroundDispatch(this, mNfcAdapter)
        super.onPause()
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

    override fun onCheckedChanged(buttonView : CompoundButton?, isChecked : Boolean) {
        if (buttonView == binder.toggleButton)
            viewModel.onCheckNFC(isChecked)
    }

    override fun onTagDiscovered(tag : Tag?) {
        binder.viewModel?.readTag(tag)
        NdefReaderTask().execute(tag)
       // tag?.let { processTagInfo(it) }
    }

    private fun handleIntent(intent: Intent) {
        Toast.makeText(this, "" + intent.action, Toast.LENGTH_SHORT).show()
        val action = intent.action
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
            val type = intent.type
            if (MIME_TEXT_PLAIN == type) {
                val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
                NdefReaderTask().execute(tag)
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
                    NdefReaderTask().execute(tag)
                    break
                }
            }
        }
    }

    /**
     * Background task for reading the data. Do not block the UI thread while reading.
     *
     * @author Ralf Wondratschek
     */
    private inner class NdefReaderTask : AsyncTask<Tag?, Void?, String?>() {
         override fun doInBackground(vararg params: Tag?): String? {
            val tag = params[0]
            val ndef = Ndef.get(tag)
                ?: // NDEF is not supported by this Tag.
                return "NDEF is not supported by this Tag"
            val ndefMessage = ndef.cachedNdefMessage
            val records = ndefMessage.records
            for (ndefRecord in records) {
                if (ndefRecord.tnf == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(
                        ndefRecord.type,
                        NdefRecord.RTD_TEXT
                    )
                ) {
                    try {
                        return readText(ndefRecord)
                    } catch (e: UnsupportedEncodingException) {
                        Log.e(TAG, "Unsupported Encoding", e)
                    }
                }
            }
            return null
        }

        @Throws(UnsupportedEncodingException::class)
        private fun readText(record: NdefRecord): String {
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
            return Common.getText(payload, languageCodeLength, textEncoding)
        }

        override fun onPostExecute(result: String?) {
            if (result != null) {
                binder.textViewExplanation.text = "Read content: $result"
            }
        }
    }

    private fun processTagInfo(tag: Tag) {
        val nfcA = NfcA.get(tag)
        if (nfcA != null) {
            try {
                nfcA.connect()
                Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show()
                val data = nfcA.transceive(byteArrayOf(0x3A.toByte(), 0xF0.toByte(), 0xFF.toByte()))
                Toast.makeText(this, "Received byte data" + String(data), Toast.LENGTH_SHORT)
                    .show()
                binder.tvConcentrationValue.text = """
                    ${binder.tvConcentrationValue!!.text}
                    ${getHex(data)}
                    """.trimIndent()
            } catch (e: Exception) {
                Toast.makeText(this, "Exception : " + e.message, Toast.LENGTH_SHORT).show()
            } finally {
                try {
                    nfcA.close()
                } catch (e: Exception) {
                    Log.e(TAG, "doInBackground: " + e.message, e)
                }
            }
        } else Toast.makeText(this, "nfca is null", Toast.LENGTH_SHORT).show()
    }

    private fun getHex(bytes: ByteArray): String {
        Log.v("tag", "Getting hex")
        val sb = StringBuilder()
        for (i in bytes.indices.reversed()) {
            val b = bytes[i].toInt() and 0xff
            if (b < 0x10) sb.append('0')
            sb.append(Integer.toHexString(b))
            if (i > 0) {
                sb.append(" ")
            }
        }
        return sb.toString()
    }

    companion object {
        const val MIME_TEXT_PLAIN = "text/plain"
        private val TAG = MainActivity::class.java.simpleName

        /**
         * @param activity The corresponding [Activity] requesting the foreground dispatch.
         * @param adapter  The [NfcAdapter] used for the foreground dispatch.
         */
        fun setupForegroundDispatch(activity: Activity, adapter: NfcAdapter?) {
            val intent = Intent(activity.applicationContext, activity.javaClass)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            val pendingIntent = PendingIntent.getActivity(
                activity.applicationContext,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            val filters = arrayOfNulls<IntentFilter>(1)
            val techList = arrayOf<Array<String>>()

            // Notice that this is the same filter as in our manifest.
            filters[0] = IntentFilter()
            filters[0]!!.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED)
            filters[0]!!.addCategory(Intent.CATEGORY_DEFAULT)
            try {
                filters[0]!!.addDataType(MIME_TEXT_PLAIN)
            } catch (e: MalformedMimeTypeException) {
                throw RuntimeException("Check your mime type.")
            }
            adapter!!.enableForegroundDispatch(activity, pendingIntent, filters, techList)
        }

        /**
         * @param activity The corresponding [] requesting to stop the foreground dispatch.
         * @param adapter  The [NfcAdapter] used for the foreground dispatch.
         */
        fun stopForegroundDispatch(activity: Activity?, adapter: NfcAdapter?) {
            adapter!!.disableForegroundDispatch(activity)
        }
    }
}