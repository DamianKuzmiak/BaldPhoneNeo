package app.baldphone.neo.settings.help

import androidx.annotation.StringRes

import app.baldphone.neo.R

data class Credit(
    @param:StringRes val nameRes: Int,
    @param:StringRes val taskRes: Int
)

val CREDITS =
    listOf(
        Credit(R.string.credit_name_uriah, R.string.credit_task_uriah),
        Credit(R.string.credit_name_ido, R.string.credit_task_ido_sharon),
        Credit(R.string.credit_name_nadav, R.string.credit_task_nadav),
        Credit(R.string.credit_name_cs, R.string.credit_task_cs),
        Credit(R.string.credit_name_de, R.string.credit_task_de),
        Credit(R.string.credit_name_el, R.string.credit_task_el),
        Credit(R.string.credit_name_es, R.string.credit_task_es),
        Credit(R.string.credit_name_fr, R.string.credit_task_fr),
        Credit(R.string.credit_name_id, R.string.credit_task_id),
        Credit(R.string.credit_name_it, R.string.credit_task_it),
        Credit(R.string.credit_name_ka, R.string.credit_task_ka),
        Credit(R.string.credit_name_ko, R.string.credit_task_ko),
        Credit(R.string.credit_name_pl, R.string.credit_task_pl),
        Credit(R.string.credit_name_pt, R.string.credit_task_pt),
        Credit(R.string.credit_name_pt_br, R.string.credit_task_pt_br),
        Credit(R.string.credit_name_ro, R.string.credit_task_ro),
        Credit(R.string.credit_name_ru, R.string.credit_task_ru),
        Credit(R.string.credit_name_sl, R.string.credit_task_sl),
        Credit(R.string.credit_name_sv, R.string.credit_task_sv),
        Credit(R.string.credit_name_tr, R.string.credit_task_tr),
        Credit(R.string.credit_name_zh, R.string.credit_task_zh)
    )
