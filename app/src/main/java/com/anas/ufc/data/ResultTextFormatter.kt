package com.anas.ufc.data

import android.content.Context
import com.anas.ufc.R

fun FightResult.toDisplayText(context: Context): String {
    val displayMethod = when (methodNormalized) {
        "submission", "technical_submission" -> context.getString(R.string.result_submission)
        "ko_tko" -> context.getString(R.string.result_ko_tko)
        "decision", "decision_unanimous", "decision_split", "decision_majority", "draw" ->
            context.getString(R.string.result_decision)
        "dq" -> context.getString(R.string.result_disqualification)
        "no_contest" -> context.getString(R.string.result_no_contest)
        else -> method
    }
    return buildResultText(
        copy(method = displayMethod),
        context.getString(R.string.result_draw),
        context.getString(R.string.result_round_time),
        context.getString(R.string.result_method_and_round),
        context.getString(R.string.result_method_only),
        context.getString(R.string.text_separator),
    )
}
