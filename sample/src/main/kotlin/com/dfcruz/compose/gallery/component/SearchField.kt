package com.dfcruz.compose.gallery.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.dfcruz.compose.gallery.annotations.Gallery

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        modifier = modifier,
        value = query,
        onValueChange = onQueryChange,
        label = {
            Text("Search")
        },
    )
}

@Gallery(
    name = "Search Field",
    group = "Inputs",
    tags = ["search", "text"]
)
@Preview
@Composable
private fun SearchFieldPreview() {
    MaterialTheme {
        SearchField(
            query = "",
            onQueryChange = {},
        )
    }
}