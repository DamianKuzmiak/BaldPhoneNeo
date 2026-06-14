package app.baldphone.neo.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.baldphone.neo.data.Prefs
import app.baldphone.neo.extensions.setClickableAccessibilityRole
import com.bald.uriah.baldphone.R

class WelcomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_welcome, container, false).apply {
        Prefs.lastSetupFragment = R.id.welcomeFragment

        findViewById<View>(R.id.btnContinue).setOnClickListener {
            findNavController().navigate(R.id.action_welcomeFragment_to_accessibilityLevelFragment)
        }
        findViewById<View>(R.id.btnSkipAll).apply {
            setClickableAccessibilityRole()
            setOnClickListener {
                Prefs.isSetupSkipped = true
                Prefs.isSetupComplete = false
                finishSetupAndGoHome()
            }
        }
    }
}
