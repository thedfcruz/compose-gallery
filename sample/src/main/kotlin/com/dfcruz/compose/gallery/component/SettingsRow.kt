package com.dfcruz.compose.gallery.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dfcruz.compose.gallery.annotations.Gallery

@Composable
fun SettingsRow(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
        )

        Switch(
            checked = checked,
            onCheckedChange = {},
        )
    }
}

@Gallery(
    name = "Settings Row",
    group = "Lists",
    tags = ["settings", "switch"]
)
@Preview
@Composable
private fun SettingsRowPreview() {
    MaterialTheme {
        SettingsRow(
            title = "Notifications",
            checked = true,
        )
    }
}