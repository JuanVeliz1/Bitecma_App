package com.bitecma.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchableDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onItemSelected: (T) -> Unit,
    showAddNew: Boolean = false,
    onAddNewValue: ((String) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredItems = items.filter { itemLabel(it).contains(value, ignoreCase = true) }
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF0D47A1) else Color.White
    val textColor = if (isDark) Color.White else Color.Black
    val borderColor = if (isDark) Color(0xFF1976D2) else Color(0xFFF1F3F5)
    val focusedColor = if (isDark) Color(0xFFBBDEFB) else Color(0xFF003366)

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            placeholder = { Text(placeholder, fontSize = 13.sp, color = if (isDark) Color.LightGray else Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = {
                    when {
                        filteredItems.size == 1 -> {
                            onItemSelected(filteredItems.first())
                            expanded = false
                        }

                        else -> {
                            expanded = filteredItems.isNotEmpty() || showAddNew
                        }
                    }
                }
            ),
            textStyle = TextStyle(color = textColor, fontSize = 14.sp),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null, tint = focusedColor)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = borderColor,
                focusedBorderColor = focusedColor,
                unfocusedTextColor = textColor,
                focusedTextColor = textColor,
                unfocusedContainerColor = bgColor,
                focusedContainerColor = bgColor
            )
        )

        if (expanded && (filteredItems.isNotEmpty() || showAddNew)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = bgColor,
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, borderColor)
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    if (showAddNew && value.isNotBlank() && filteredItems.none { itemLabel(it).equals(value, true) }) {
                        item {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, null, tint = Color(0xFF00897B), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Agregar '$value'...", color = Color(0xFF00897B), fontWeight = FontWeight.Bold)
                                    }
                                },
                                onClick = {
                                    onAddNewValue?.invoke(value.trim())
                                    expanded = false
                                }
                            )
                            HorizontalDivider(color = borderColor)
                        }
                    }
                    items(filteredItems) { item ->
                        DropdownMenuItem(
                            text = { Text(itemLabel(item), fontSize = 14.sp, color = textColor) },
                            onClick = {
                                onItemSelected(item)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
