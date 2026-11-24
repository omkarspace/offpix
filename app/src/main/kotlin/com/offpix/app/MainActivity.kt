package com.offpix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offpix.app.ui.editor.EditorScreen
import com.offpix.app.ui.home.HomeScreen
import com.offpix.app.ui.sheets.EngineLicensesSheet
import com.offpix.app.ui.sheets.ExportBottomSheet
import com.offpix.app.ui.theme.OffPixTheme
import com.offpix.app.ui.theme.StudioColors
import com.offpix.app.viewmodel.StudioScreen
import com.offpix.app.viewmodel.StudioTab
import com.offpix.app.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OffPixTheme {
                val vm: StudioViewModel = viewModel()
                val state by vm.uiState.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                var pendingTab by remember { mutableStateOf(StudioTab.UPSCALE) }

                val photoPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    if (uri != null) {
                        vm.onPhotoSelected(uri, pendingTab)
                    }
                }

                LaunchedEffect(state.toastMessage) {
                    state.toastMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        vm.clearToast()
                    }
                }

                Scaffold(
                    containerColor = StudioColors.Bg,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        when (state.screen) {
                            StudioScreen.HOME -> HomeScreen(
                                recentRescues = state.recentRescues,
                                onPickPhoto = { tab ->
                                    pendingTab = tab
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onOpenEngineSheet = { vm.setShowEngineSheet(true) }
                            )

                            StudioScreen.EDITOR -> EditorScreen(
                                state = state,
                                onBackHome = vm::backToHome,
                                onPickAnotherPhoto = {
                                    pendingTab = state.activeTab
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onUndo = vm::undoEdit,
                                onRedo = vm::redoEdit,
                                onOpenEngineSheet = { vm.setShowEngineSheet(true) },
                                onOpenExportSheet = { vm.setShowExportSheet(true) },
                                onSelectTab = vm::selectTab,
                                onScaleFactorChange = vm::setUpscaleFactor,
                                onUpscaleSharpnessChange = vm::setUpscaleSharpness,
                                onRunUpscale = vm::runUpscale,
                                onUnblurModeChange = vm::setUnblurMode,
                                onUnblurStrengthChange = vm::setUnblurStrength,
                                onRunUnblur = vm::runUnblurOrFaceRestore,
                                onEraserToolChange = vm::setEraserTool,
                                onBrushSizeChange = vm::setBrushSize,
                                onTapMask = vm::onTapSelectObject,
                                onBrushStroke = vm::onBrushStroke,
                                onClearMask = vm::clearMask,
                                onRunErase = vm::runMagicErase,
                                onCancelProcessing = vm::cancelActiveJob
                            )
                        }

                        if (state.showExportSheet) {
                            val bmp = state.afterBitmap ?: state.beforeBitmap
                            ExportBottomSheet(
                                width = bmp?.width ?: 0,
                                height = bmp?.height ?: 0,
                                selectedFormat = state.exportFormat,
                                preserveExif = state.preserveExif,
                                onFormatChange = vm::setExportFormat,
                                onPreserveExifChange = vm::setPreserveExif,
                                onConfirmSave = vm::saveCurrentPhoto,
                                onDismiss = { vm.setShowExportSheet(false) }
                            )
                        }

                        if (state.showEngineSheet) {
                            EngineLicensesSheet(
                                activeAccelerator = state.activeAccelerator,
                                onDismiss = { vm.setShowEngineSheet(false) }
                            )
                        }
                    }
                }
            }
        }
    }
}
