package com.dfcruz.compose.gallery.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dfcruz.compose.gallery.annotations.Gallery

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(80.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Gallery(
    name = "Loading",
    group = "Feedback",
    tags = ["loading"]
)
@Preview
@Composable
private fun LoadingIndicatorPreview() {
    MaterialTheme {
        LoadingIndicator()
    }
}