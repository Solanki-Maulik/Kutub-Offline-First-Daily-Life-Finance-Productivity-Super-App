package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.Person

@Composable
fun AddPersonDialog(
    initialPerson: Person? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, note: String) -> Unit
) {
    var name by remember { mutableStateOf(initialPerson?.name ?: "") }
    var phone by remember { mutableStateOf(initialPerson?.phone ?: "") }
    var note by remember { mutableStateOf(initialPerson?.note ?: "") }
    var nameError by remember { mutableStateOf(false) }

    val isEditing = initialPerson != null

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                if (isEditing) "Edit Person" else "Add Person",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (isEditing) "Update contact details for this person."
                    else "Enter details for the person you lend to or borrow from.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = false
                    },
                    label = { Text("Person Name *") },
                    placeholder = { Text("e.g. Rahul Sharma") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    isError = nameError,
                    supportingText = {
                        if (nameError) {
                            Text("Person name is required", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_person_name_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (Optional)") },
                    placeholder = { Text("e.g. 9876543210") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_person_phone_input")
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Relation (Optional)") },
                    placeholder = { Text("e.g. Colleague, Flatmate, Shopkeeper") },
                    leadingIcon = {
                        Icon(Icons.Default.Notes, contentDescription = null)
                    },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_person_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else {
                        onSave(name.trim(), phone.trim(), note.trim())
                    }
                },
                modifier = Modifier.testTag("save_person_button")
            ) {
                Text(if (isEditing) "Save Changes" else "Add Person")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_person_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
