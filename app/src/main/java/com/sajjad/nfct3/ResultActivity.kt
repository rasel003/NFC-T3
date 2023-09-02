package com.sajjad.nfct3

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.sajjad.nfct3.databinding.ActivityBinder
import com.sajjad.nfct3.preference.AppPrefsManager
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
}