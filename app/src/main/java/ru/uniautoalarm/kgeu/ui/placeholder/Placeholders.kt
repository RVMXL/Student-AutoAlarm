package ru.uniautoalarm.kgeu.ui.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// TODO(следующий этап): заменить вызовы заглушек в NavHost (MainActivity.kt)
//  на реальные экраны HomeScreen / ScheduleScreen / SettingsScreen
//  и УДАЛИТЬ этот файл.

/** Временная заглушка главного экрана (большой тумблер будет здесь). */
@Composable
fun HomePlaceholder() {
    PlaceholderCard(
        title = "Главный экран",
        subtitle = "Большой тумблер авто-будильника и ближайший будильник появятся здесь"
    )
}

/** Временная заглушка экрана расписания (бумажный календарь). */
@Composable
fun SchedulePlaceholder() {
    PlaceholderCard(
        title = "Расписание",
        subtitle = "Неделя пар из кэша КГЭУ и «бумажный календарь» появятся здесь"
    )
}

/** Временная заглушка экрана настроек. */
@Composable
fun SettingsPlaceholder() {
    PlaceholderCard(
        title = "Настройки",
        subtitle = "Тема, акцент, режим будильника, исключения появятся здесь"
    )
}

@Composable
private fun PlaceholderCard(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Rounded.Construction,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "$title — экран в разработке",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
