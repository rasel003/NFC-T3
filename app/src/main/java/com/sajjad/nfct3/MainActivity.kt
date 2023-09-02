package com.sajjad.nfct3

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.sajjad.nfct3.databinding.ActivityMainBinder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


/**
 * Activity for reading data from an NDEF Tag.
 *
 * @author Ralf Wondratschek
 */
class MainActivity : AppCompatActivity(),
    NfcAdapter.ReaderCallback {

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
                        val returnIntent = intent
                         returnIntent.putExtra(Common.result, it)
                         setResult(RESULT_OK, returnIntent)
                         finish()
                    }
                })
            })
        }
    }


    override fun onTagDiscovered(tag: Tag?) {
//        binder.viewModel?.readTag(tag)
        binder.viewModel?.readTagNdefData(tag)
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