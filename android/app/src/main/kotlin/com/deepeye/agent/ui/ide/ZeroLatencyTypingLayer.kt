package com.deepeye.agent.ui.ide

import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * ZeroLatencyTypingLayer bypasses Jetpack Compose state-hoisting on the hot keystroke path.
 * It directly uses the native Android EditText and buffers the text locally.
 * Compose only recomposes this view when the physical tree changes, not on every keystroke.
 */
@Composable
fun ZeroLatencyTypingLayer(
    modifier: Modifier = Modifier,
    placeholder: String = "Type here...",
    onTextAvailable: (String) -> Unit
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp, max = 150.dp)
            .padding(8.dp),
        factory = { context ->
            EditText(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                hint = placeholder
                setBackgroundResource(android.R.color.transparent)
                setTextColor(android.graphics.Color.WHITE)
                setHintTextColor(android.graphics.Color.GRAY)
                maxLines = 4
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE

                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        onTextAvailable(s?.toString() ?: "")
                    }
                })
            }
        },
        update = { view ->
            view.hint = placeholder
        }
    )
}
