package app.baldphone.neo.settings.help

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView

import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.io.IOException

import app.baldphone.neo.settings.BaseSettingsFragment

import com.bald.uriah.baldphone.R

class LicenseFragment : BaseSettingsFragment(R.layout.fragment_licenses) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val licensesTextView = view.findViewById<TextView>(R.id.licenses_text)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val text =
                    withContext(Dispatchers.IO) {
                        requireContext().assets.open("LICENSE").use { input ->
                            input.bufferedReader().use { reader ->
                                reader.readLines().joinToString("\n") { it.trim() }
                            }
                        }
                    }
                licensesTextView.text = text
            } catch (e: IOException) {
                Log.e("LicenseFragment", "Failed to read license file", e)
                licensesTextView.text = "Error reading license file: ${e.message}"
            }
        }
    }
}
