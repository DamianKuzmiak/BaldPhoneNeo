package app.baldphone.neo.settings.system

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration

import kotlinx.coroutines.launch

import app.baldphone.neo.permissions.PermissionManager
import app.baldphone.neo.permissions.PermissionRepository
import app.baldphone.neo.permissions.model.AppPermission
import app.baldphone.neo.permissions.ui.PermissionListAdapter
import app.baldphone.neo.permissions.ui.PermissionUiModel
import app.baldphone.neo.settings.BaseSettingsFragment

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.databinding.FragmentPermissionsBinding

class PermissionsFragment : BaseSettingsFragment() {
    private var binding: FragmentPermissionsBinding? = null

    private val adapter by lazy {
        PermissionListAdapter { permission ->
            PermissionManager.checkOrRequest(requireActivity(), permission) { _ ->
                PermissionRepository.refresh()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val divider = DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        ContextCompat.getDrawable(requireContext(), R.drawable.ll_divider)?.let { divider.setDrawable(it) }

        binding!!.permissionsList.apply {
            addItemDecoration(divider)
            adapter = this@PermissionsFragment.adapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PermissionRepository
                    .getMissingPermissionsFlow(requireContext())
                    .collect { missingPermissions ->
                        updateUi(missingPermissions)
                    }
            }
        }
    }

    private fun updateUi(missingPermissions: List<AppPermission>) {
        val permissionItems =
            missingPermissions
                .map { permission ->
                    PermissionUiModel(
                        permission = permission,
                        isMandatory = PermissionRepository.mandatoryPolicy.isMandatory(requireContext(), permission)
                    )
                }.sortedByDescending { it.isMandatory }

        adapter.submitList(permissionItems)

        val isEmpty = missingPermissions.isEmpty()
        binding!!.apply {
            permissionsList.isVisible = !isEmpty
            textView4.isVisible = !isEmpty
            emptyState.isVisible = isEmpty
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
