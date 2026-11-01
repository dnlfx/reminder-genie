package com.dnlfx.remindergenie.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dnlfx.remindergenie.core.ItemTemplate
import com.dnlfx.remindergenie.ui.components.IconBadge
import com.dnlfx.remindergenie.ui.components.ItemIcons
import com.dnlfx.remindergenie.ui.components.readableWidth
import com.dnlfx.remindergenie.ui.components.segmentShape
import com.dnlfx.remindergenie.ui.theme.color

/** Pick a template (or start blank), then fine-tune it in the editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    onBack: () -> Unit,
    onTemplate: (ItemTemplate) -> Unit,
    onCustom: (name: String) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val query = search.trim()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add item") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.Close, contentDescription = "Cancel") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding().readableWidth(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search, or name your own item") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = if (search.isNotEmpty()) {
                        { IconButton(onClick = { search = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Clear") } }
                    } else {
                        null
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (query.isNotEmpty()) onCustom(query) }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("template-search"),
                )
            }
            item(key = "custom") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { onCustom(query) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("custom-item"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(Icons.Outlined.Add, MaterialTheme.colorScheme.primary, filled = true)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (query.isEmpty()) "Custom item" else "Create “$query”",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "Set your own name, schedule and icon",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            for (category in ItemTemplate.Category.entries) {
                val templates = ItemTemplate.templatesIn(category)
                    .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
                if (templates.isEmpty()) continue
                item(key = category.name) {
                    Text(
                        category.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
                    )
                }
                itemsIndexed(templates, key = { _, template -> template.name }) { index, template ->
                    TemplateRow(
                        icon = ItemIcons.forName(template.symbolName),
                        template = template,
                        shape = segmentShape(index, templates.size),
                        onClick = { onTemplate(template) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(icon: ImageVector, template: ItemTemplate, shape: Shape, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("template-${template.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, template.color.color)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(template.name, style = MaterialTheme.typography.bodyLarge)
            Text(template.intervalDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
