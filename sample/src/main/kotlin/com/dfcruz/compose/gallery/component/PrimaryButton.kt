package com.dfcruz.compose.gallery.component

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.dfcruz.compose.gallery.annotations.Gallery

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier,
        onClick = onClick,
    ) {
        Text(text)
    }
}

@Gallery(
    name = "Primary Button",
    group = "Buttons",
    tags = ["primary", "filled"]
)
@Preview
@Composable
private fun PrimaryButtonPreview() {
    MaterialTheme {
        PrimaryButton(
            text = "Continue",
            onClick = {},
        )
    }
}