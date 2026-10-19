package pm.koc.poptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * Text input that is safe to navigate with a D-pad: focusing it does not open the keyboard (which would
 * trap focus). Pressing OK starts editing and shows the IME; Done or leaving the field stops editing.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    secret: Boolean = false,
    singleLine: Boolean = true,
    startEditing: Boolean = false,
) {
    var editing by remember { mutableStateOf(startEditing) }
    var fieldHadFocus by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val surfaceFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(editing) {
        if (editing) {
            fieldFocus.safeRequestFocus()
            keyboard?.show()
        }
    }

    Surface(
        onClick = { editing = true },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(surfaceFocus)
            .focusProperties { canFocus = !editing },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colors.surfaceVariant,
            focusedContainerColor = colors.surfaceVariant,
            pressedContainerColor = colors.surfaceVariant,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (editing) colors.primary else colors.borderVariant)),
            focusedBorder = Border(BorderStroke(2.dp, colors.border)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onAny = {
                        editing = false
                        keyboard?.hide()
                        surfaceFocus.safeRequestFocus()
                        onImeAction()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .focusProperties { canFocus = editing }
                        // A single-line field swallows up/down; after BACK closed the keyboard that would trap
                        // the D-pad here, so up/down end editing and move on.
                        .onPreviewKeyEvent {
                            val direction = when (it.key) {
                                Key.DirectionDown -> FocusDirection.Down
                                Key.DirectionUp -> FocusDirection.Up
                                else -> return@onPreviewKeyEvent false
                            }
                            if (it.type == KeyEventType.KeyDown) {
                                editing = false
                                keyboard?.hide()
                                if (!focusManager.moveFocus(direction)) surfaceFocus.safeRequestFocus()
                            }
                            true
                        }
                        .onFocusChanged {
                            // Only a real focus loss ends editing (not the initial unfocused callback).
                            if (it.isFocused) {
                                fieldHadFocus = true
                            } else if (fieldHadFocus) {
                                fieldHadFocus = false
                                editing = false
                            }
                        },
                )
            }
        }
    }
}
