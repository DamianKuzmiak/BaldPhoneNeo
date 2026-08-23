package app.baldphone.neo.settings.system

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.util.Locale

import org.xmlpull.v1.XmlPullParser

import app.baldphone.neo.extensions.AccessibilityRole
import app.baldphone.neo.extensions.setClickableAccessibilityRole
import app.baldphone.neo.settings.BaseSettingsFragment
import app.baldphone.neo.utils.LocaleUtils
import app.baldphone.neo.utils.startActivitySafe

import com.bald.uriah.baldphone.R
import com.bald.uriah.baldphone.databinding.FragmentLanguageSelectorBinding
import com.bald.uriah.baldphone.databinding.ItemLanguageBinding

class LanguageFragment : BaseSettingsFragment(R.layout.fragment_language_selector) {
    private var binding: FragmentLanguageSelectorBinding? = null

    private data class LanguageOption(
        val tag: String,
        val displayName: String,
        val localizedName: String
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentLanguageSelectorBinding.bind(view)

        val recyclerView = binding!!.languageList

        val divider = DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        ContextCompat.getDrawable(requireContext(), R.drawable.ll_divider)?.let {
            divider.setDrawable(it)
            recyclerView.addItemDecoration(divider)
        }

        val defaultLocale = Locale.getDefault()
        val context = requireContext()

        viewLifecycleOwner.lifecycleScope.launch {
            val languages =
                withContext(Dispatchers.Default) {
                    val systemDefault = getSystemOption(context, defaultLocale)
                    listOf(systemDefault) + buildLanguageList(defaultLocale)
                }
            val currentTag = getCurrentLocaleTag()
            recyclerView.adapter =
                LanguageAdapter(languages, currentTag) { option ->
                    if (option.tag != currentTag) {
                        LocaleUtils.applyLocale(option.tag)
                    } else if (option.tag == "") {
                        requireContext().startActivitySafe(Intent(Settings.ACTION_LOCALE_SETTINGS))
                    }
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun getSystemOption(context: Context, defaultLocale: Locale): LanguageOption {
        val systemLocale = LocaleUtils.getSystemLocale(context)
        val systemLanguageName =
            systemLocale.getDisplayLanguage(defaultLocale).replaceFirstChar { it.titlecase(defaultLocale) }
        return LanguageOption(
            tag = "",
            displayName = getString(R.string.follow_system_language),
            localizedName = systemLanguageName
        )
    }

    private fun parseSupportedLocales(): List<String> {
        val locales = mutableListOf<String>()
        val parser = resources.getXml(R.xml.locales_config)

        @Suppress("TooGenericExceptionCaught")
        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType != XmlPullParser.START_TAG || parser.name != "locale") {
                    eventType = parser.next()
                    continue
                }

                val name = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                if (name != null) {
                    locales.add(name)
                }

                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("LanguageFragment", "Failed to parse supported locales", e)
        } finally {
            parser.close()
        }
        return locales
    }

    private fun buildLanguageList(userLocale: Locale): List<LanguageOption> =
        parseSupportedLocales()
            .map { tag ->
                val locale = Locale.forLanguageTag(tag)
                LanguageOption(
                    tag = tag,
                    displayName = locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) },
                    localizedName = locale.getDisplayName(userLocale).replaceFirstChar { it.titlecase(userLocale) }
                )
            }.sortedBy { it.displayName.lowercase() }

    private fun getCurrentLocaleTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return ""
        return locales.get(0)?.toLanguageTag() ?: ""
    }

    private class LanguageAdapter(
        private val items: List<LanguageOption>,
        private val selectedTag: String,
        private val onItemClick: (LanguageOption) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            val holder =
                ViewHolder(binding) { position ->
                    if (position != RecyclerView.NO_POSITION) {
                        onItemClick(items[position])
                    }
                }
            return holder
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val option = items[position]
            val isSelected = (option.tag == selectedTag)
            holder.bind(option, isSelected)
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(
            private val binding: ItemLanguageBinding,
            private val onClick: (Int) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {
            init {
                binding.root.setOnClickListener {
                    onClick(bindingAdapterPosition)
                }
                binding.root.setClickableAccessibilityRole(AccessibilityRole.RADIO_BUTTON)
            }

            fun bind(option: LanguageOption, isSelected: Boolean) {
                binding.languageDisplayName.text = option.displayName
                binding.languageLocaleName.text = option.localizedName
                binding.languageLocaleName.isVisible = option.localizedName.isNotEmpty()
                binding.radioButton.isChecked = isSelected

                binding.root.contentDescription = option.displayName
                binding.root.isSelected = isSelected
            }
        }
    }
}
