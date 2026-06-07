package com.pabloufor.voiceflow.presentation.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.pabloufor.voiceflow.R
import com.pabloufor.voiceflow.domain.model.Persona

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    showResetAction: Boolean,
    onResetClick: () -> Unit,
    onSettingsClick: () -> Unit,
    selectedPersona: Persona = Persona.Friendly,
    onPersonaSelected: (Persona) -> Unit = {},
) {
    var personaMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(stringResource(R.string.chat_screen_title)) },
        actions = {
            Text(
                text = stringResource(selectedPersona.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = { personaMenuExpanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_persona),
                    contentDescription = stringResource(R.string.chat_persona_cd),
                    tint = MaterialTheme.colorScheme.primary,
                )
                DropdownMenu(
                    expanded = personaMenuExpanded,
                    onDismissRequest = { personaMenuExpanded = false },
                ) {
                    Persona.entries.forEach { persona ->
                        DropdownMenuItem(
                            text = { Text(stringResource(persona.labelRes)) },
                            onClick = {
                                onPersonaSelected(persona)
                                personaMenuExpanded = false
                            },
                        )
                    }
                }
            }
            if (showResetAction) {
                IconButton(onClick = onResetClick) {
                    Icon(
                        painter = painterResource(R.drawable.new_conversation),
                        contentDescription = stringResource(R.string.chat_reset_title),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings_cd_settings),
                )
            }
        },
    )
}

private val Persona.labelRes: Int
    get() = when (this) {
        Persona.Friendly -> R.string.persona_friendly
        Persona.Parent -> R.string.persona_parent
        Persona.Teacher -> R.string.persona_teacher
    }
