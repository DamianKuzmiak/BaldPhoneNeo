package app.baldphone.neo.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.baldphone.neo.data.AccessibilityLevel
import app.baldphone.neo.data.Prefs

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.databinding.FragmentAccessibilityLevelBinding

class AccessibilitySetupFragment : Fragment() {

    private var _binding: FragmentAccessibilityLevelBinding? = null
    private val binding get() = _binding!!

    private var isTestActive = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAccessibilityLevelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Prefs.lastSetupFragment = R.id.accessibilityLevelFragment

        // Setup radio group
        val currentLevel = Prefs.accessibilityLevel
        binding.rgLevels.check(levelToId(currentLevel))
        binding.rgLevels.setOnCheckedChangeListener { _, checkedId ->
            val chosen = idToLevel(checkedId)
            if (chosen != currentLevel) {
                Prefs.accessibilityLevel = chosen
                (requireActivity() as? SetupActivity)?.restart()
            }
        }

        // Setup haptic feedback toggle
        binding.cbHapticFeedback.isChecked = Prefs.isVibrationFeedbackEnabled
        binding.cbHapticFeedback.setOnClickListener {
            Prefs.isVibrationFeedbackEnabled = binding.cbHapticFeedback.isChecked
        }

        binding.btnTest.setOnClickListener {
            isTestActive = !isTestActive

            val colorRes = if (isTestActive) R.color.green else android.R.color.transparent
            it.setBackgroundColor(
                ContextCompat.getColor(requireContext(), colorRes)
            )
        }

        binding.btnContinue.setOnClickListener {
            findNavController().navigate(R.id.action_accessibilityLevelFragment_to_permissionsIntroFragment)
        }
    }

    private fun levelToId(level: AccessibilityLevel) = when (level) {
        AccessibilityLevel.ENHANCED -> R.id.rbEnhanced
        AccessibilityLevel.FULL -> R.id.rbFull
        AccessibilityLevel.BASIC -> R.id.rbBasic
    }

    private fun idToLevel(id: Int) = when (id) {
        R.id.rbEnhanced -> AccessibilityLevel.ENHANCED
        R.id.rbFull -> AccessibilityLevel.FULL
        else -> AccessibilityLevel.BASIC
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
