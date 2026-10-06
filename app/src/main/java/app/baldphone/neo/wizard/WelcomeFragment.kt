package app.baldphone.neo.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.Fragment

import app.baldphone.neo.R

import com.bald.uriah.baldphone.views.ViewPagerHolder

class WelcomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        inflater.inflate(R.layout.fragment_welcome, container, false).apply {
            findViewById<View>(R.id.btnContinue).setOnClickListener {
                val viewPagerHolder = activity?.findViewById<ViewPagerHolder>(R.id.view_pager_holder)
                if (viewPagerHolder != null) {
                    viewPagerHolder.setCurrentItem(viewPagerHolder.pageIndex + 1)
                } else {
                    finishSetupAndGoHome()
                }
            }
            findViewById<View>(R.id.btnSkipAll).setOnClickListener {
                finishSetupAndGoHome()
            }
        }
}
