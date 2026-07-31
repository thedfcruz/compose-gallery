package com.dfcruz.compose.gallery.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dfcruz.compose.gallery.annotations.Gallery

data class User(
    val name: String,
    val email: String,
)

@Composable
fun UserCard(
    user: User,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        Column(
            Modifier.padding(16.dp)
        ) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = user.email,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Gallery(
    name = "User Card",
    group = "Cards",
    tags = ["user", "profile"]
)
@Preview
@Composable
private fun UserCardPreview() {
    MaterialTheme {
        UserCard(
            user = User(
                name = "John Doe",
                email = "john@example.com",
            ),
        )
    }
}