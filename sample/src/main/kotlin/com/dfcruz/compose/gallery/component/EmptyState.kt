package com.dfcruz.compose.gallery.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dfcruz.compose.gallery.annotations.Gallery

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(
            Modifier.height(8.dp),
        )

        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Gallery(
    name = "Empty State",
    group = "States",
    tags = ["empty"]
)
@Preview
@Composable
private fun EmptyStatePreview() {
    MaterialTheme {
        EmptyState(
            title = "No Messages",
            subtitle = "You're all caught up.",
        )
    }
}