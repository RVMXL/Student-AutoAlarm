package ru.uniautoalarm.kgeu.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Список выбора языка (радиокнопки): используется в первом шаге мастера
 * настройки и в диалоге «Язык» на настройках. Названия — родное имя +
 * имя на текущем языке («English / Английский»).
 *
 * SET-1: первой строкой идёт «язык системы» (пустой тег) — раньше после выбора
 * конкретного языка вернуться к системному было невозможно, хотя README обещал.
 *
 * @param selected текущий тег языка (пустая строка — язык системы).
 * @param onSelect вызывается с тегом выбранного языка (может быть пустым).
 */
@Composable
fun LanguagePicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = buildList {
        add(LanguageOption("", "")) // «язык системы» (SET-1)
        addAll(SUPPORTED_LANGUAGES)
    }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            // SET-15 (0.11.0): группа радиокнопок для скринридера — навигация
            // по стрелкам/свайпам озвучивается как выбор одного из многих
            .selectableGroup()
    ) {
        items(options, key = { it.tag.ifBlank { "system" } }) { option ->
            val isSelected = option.tag == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.RadioButton,
                        onClickLabel = option.displayName()
                    ) { onSelect(option.tag) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = isSelected, onClick = { onSelect(option.tag) })
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = option.displayName(),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (isSelected && option.nativeName.isNotBlank()) {
                        Text(
                            text = option.nativeName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
