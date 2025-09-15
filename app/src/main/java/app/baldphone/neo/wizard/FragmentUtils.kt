package app.baldphone.neo.wizard

import android.content.Intent
import androidx.fragment.app.Fragment
import com.bald.uriah.baldphone.activities.HomeScreenActivity

fun Fragment.finishSetupAndGoHome() {
    val intent = Intent(requireContext(), HomeScreenActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
    activity?.finish()
}
