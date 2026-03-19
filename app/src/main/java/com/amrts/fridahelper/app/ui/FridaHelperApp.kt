package com.amrts.fridahelper.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amrts.fridahelper.app.HookViewModel
import com.amrts.fridahelper.app.ThemeManager
import com.amrts.fridahelper.app.theme.CodeInputStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FridaHelperApp(
    themeMode: Int = ThemeManager.MODE_SYSTEM,
    onCycleTheme: () -> Unit = {},
    viewModel: HookViewModel = viewModel()
) {
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val tabs = listOf("Java Hook", "Native Hook")

    val javaScript by viewModel.javaScriptOutput.collectAsStateWithLifecycle()
    val nativeScript by viewModel.nativeScriptOutput.collectAsStateWithLifecycle()
    val composedScript by viewModel.composedScriptOutput.collectAsStateWithLifecycle()
    val hasVisibleScript = javaScript != null || nativeScript != null || composedScript != null

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    val autoScroll by viewModel.autoScrollEnabled.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    var showExportDirDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.autoScrollEnabled.value = ThemeManager.getAutoScroll(context)
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("FridaHelper") },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = {
                        onCycleTheme()
                        val label = ThemeManager.getModeLabel(ThemeManager.getSavedMode(context))
                        scope.launch { snackbarHostState.showSnackbar("Theme: $label") }
                    }) {
                        Icon(
                            imageVector = when (themeMode) {
                                ThemeManager.MODE_LIGHT -> Icons.Default.LightMode
                                ThemeManager.MODE_DARK -> Icons.Default.DarkMode
                                else -> Icons.Default.BrightnessAuto
                            },
                            contentDescription = "Toggle theme"
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Auto-scroll to output")
                                    Spacer(Modifier.width(8.dp))
                                    Switch(
                                        checked = autoScroll,
                                        onCheckedChange = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.autoScrollEnabled.value = it
                                            ThemeManager.setAutoScroll(context, it)
                                        }
                                    )
                                }
                            },
                            onClick = {
                                val newVal = !autoScroll
                                viewModel.autoScrollEnabled.value = newVal
                                ThemeManager.setAutoScroll(context, newVal)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export directory…") },
                            onClick = {
                                showMenu = false
                                showExportDirDialog = true
                            }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            SecondaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !hasVisibleScript,
                beyondBoundsPageCount = 0,
                modifier = Modifier.clipToBounds()
            ) { page ->
                when (page) {
                    0 -> JavaHookScreen(viewModel, snackbarHostState)
                    1 -> NativeHookScreen(viewModel, snackbarHostState)
                }
            }
        }
    }

    if (showExportDirDialog) {
        ExportDirDialog(
            currentPath = ThemeManager.getExportDir(context),
            onDismiss = { showExportDirDialog = false },
            onSave = { path ->
                ThemeManager.setExportDir(context, path)
                showExportDirDialog = false
                val msg = if (path.isBlank()) "Export directory: Default (Documents/FridaHelper)"
                          else "Export directory: $path"
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }
}

@Composable
private fun ExportDirDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var path by remember { mutableStateOf(currentPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Directory") },
        text = {
            Column {
                Text(
                    "Custom path for exported scripts. Leave empty to use default (Documents/FridaHelper).",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Directory path") },
                    placeholder = { Text("/sdcard/FridaScripts", style = CodeInputStyle) },
                    singleLine = true,
                    textStyle = CodeInputStyle
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(path) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
