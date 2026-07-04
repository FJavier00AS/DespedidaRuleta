package com.example.despedidaruleta.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.despedidaruleta.core.di.AppContainer
import com.example.despedidaruleta.feature.admin.AdminScreen
import com.example.despedidaruleta.feature.admin.AdminViewModel
import com.example.despedidaruleta.feature.auth.LoginScreen
import com.example.despedidaruleta.feature.auth.LoginViewModel
import com.example.despedidaruleta.feature.auth.RegisterScreen
import com.example.despedidaruleta.feature.auth.RegisterViewModel
import com.example.despedidaruleta.feature.auth.ResetPasswordScreen
import com.example.despedidaruleta.feature.auth.ResetPasswordViewModel
import com.example.despedidaruleta.feature.auth.WelcomeScreen
import com.example.despedidaruleta.feature.history.HistoryScreen
import com.example.despedidaruleta.feature.history.HistoryViewModel
import com.example.despedidaruleta.feature.lightning.LightningScreen
import com.example.despedidaruleta.feature.lightning.LightningViewModel
import com.example.despedidaruleta.feature.roulette.RouletteScreen
import com.example.despedidaruleta.feature.roulette.RouletteViewModel
import com.example.despedidaruleta.feature.session.SessionHomeScreen
import com.example.despedidaruleta.feature.session.SessionHomeViewModel
import com.example.despedidaruleta.feature.session.SessionSettingsScreen
import com.example.despedidaruleta.feature.session.SessionSettingsViewModel
import com.example.despedidaruleta.feature.settings.LocalSettingsScreen
import com.example.despedidaruleta.feature.settings.LocalSettingsViewModel
import com.example.despedidaruleta.feature.sessions.CreateSessionScreen
import com.example.despedidaruleta.feature.sessions.CreateSessionViewModel
import com.example.despedidaruleta.feature.sessions.JoinSessionScreen
import com.example.despedidaruleta.feature.sessions.JoinSessionViewModel
import com.example.despedidaruleta.feature.sessions.SessionsListScreen
import com.example.despedidaruleta.feature.sessions.SessionsListViewModel
import com.example.despedidaruleta.feature.splash.SplashScreen
import com.example.despedidaruleta.feature.splash.SplashUiState
import com.example.despedidaruleta.feature.splash.SplashViewModel

@Composable
fun DespedidaRuletaApp(container: AppContainer, initialSessionId: String? = null) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.Splash
    ) {
        composable(AppRoutes.Splash) {
            val factory = remember(container) {
                viewModelFactory {
                    initializer { SplashViewModel(container.authRepository) }
                }
            }
            val viewModel: SplashViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            SplashScreen()
            LaunchedEffect(uiState) {
                when (uiState) {
                    SplashUiState.Authenticated -> {
                        val destination = initialSessionId
                            ?.takeIf { it.isNotBlank() }
                            ?.let { AppRoutes.sessionHome(it) }
                            ?: AppRoutes.Sessions
                        navController.navigate(destination) {
                            popUpTo(AppRoutes.Splash) { inclusive = true }
                        }
                    }
                    SplashUiState.Unauthenticated -> navController.navigate(AppRoutes.Welcome) {
                        popUpTo(AppRoutes.Splash) { inclusive = true }
                    }
                    SplashUiState.Checking -> Unit
                }
            }
        }

        composable(AppRoutes.Welcome) {
            WelcomeScreen(
                onLogin = { navController.navigate(AppRoutes.Login) },
                onRegister = { navController.navigate(AppRoutes.Register) }
            )
        }

        composable(AppRoutes.Login) {
            val factory = remember(container) {
                viewModelFactory { initializer { LoginViewModel(container.authRepository) } }
            }
            val viewModel: LoginViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LoginScreen(
                uiState = uiState,
                onEmailChanged = viewModel::onEmailChanged,
                onPasswordChanged = viewModel::onPasswordChanged,
                onLogin = viewModel::login,
                onForgotPassword = { navController.navigate(AppRoutes.ResetPassword) },
                onBack = { navController.popBackStack() },
                onAuthenticated = {
                    navController.navigate(AppRoutes.Sessions) {
                        popUpTo(AppRoutes.Welcome) { inclusive = true }
                    }
                }
            )
        }

        composable(AppRoutes.Register) {
            val factory = remember(container) {
                viewModelFactory { initializer { RegisterViewModel(container.authRepository) } }
            }
            val viewModel: RegisterViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            RegisterScreen(
                uiState = uiState,
                onDisplayNameChanged = viewModel::onDisplayNameChanged,
                onEmailChanged = viewModel::onEmailChanged,
                onPasswordChanged = viewModel::onPasswordChanged,
                onRegister = viewModel::register,
                onBack = { navController.popBackStack() },
                onAuthenticated = {
                    navController.navigate(AppRoutes.Sessions) {
                        popUpTo(AppRoutes.Welcome) { inclusive = true }
                    }
                }
            )
        }

        composable(AppRoutes.ResetPassword) {
            val factory = remember(container) {
                viewModelFactory { initializer { ResetPasswordViewModel(container.authRepository) } }
            }
            val viewModel: ResetPasswordViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            ResetPasswordScreen(
                uiState = uiState,
                onEmailChanged = viewModel::onEmailChanged,
                onSendReset = viewModel::sendReset,
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.Sessions) {
            val factory = remember(container) {
                viewModelFactory {
                    initializer {
                        SessionsListViewModel(
                            authRepository = container.authRepository,
                            sessionRepository = container.sessionRepository,
                            connectivityRepository = container.connectivityRepository
                        )
                    }
                }
            }
            val viewModel: SessionsListViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            SessionsListScreen(
                uiState = uiState,
                onCreateSession = { navController.navigate(AppRoutes.CreateSession) },
                onJoinSession = { navController.navigate(AppRoutes.JoinSession) },
                onOpenSession = { sessionId -> navController.navigate(AppRoutes.sessionHome(sessionId)) },
                onSignOut = {
                    viewModel.signOut()
                    navController.navigate(AppRoutes.Welcome) {
                        popUpTo(AppRoutes.Sessions) { inclusive = true }
                    }
                }
            )
        }

        composable(AppRoutes.CreateSession) {
            val factory = remember(container) {
                viewModelFactory {
                    initializer {
                        CreateSessionViewModel(
                            authRepository = container.authRepository,
                            sessionRepository = container.sessionRepository,
                            connectivityRepository = container.connectivityRepository
                        )
                    }
                }
            }
            val viewModel: CreateSessionViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            CreateSessionScreen(
                uiState = uiState,
                onEventNameChanged = viewModel::onEventNameChanged,
                onGroomNameChanged = viewModel::onGroomNameChanged,
                onCreate = viewModel::createSession,
                onBack = { navController.popBackStack() },
                onNavigateToSession = { sessionId ->
                    navController.navigate(AppRoutes.sessionHome(sessionId)) {
                        popUpTo(AppRoutes.Sessions)
                    }
                },
                onNavigationConsumed = viewModel::navigationConsumed
            )
        }

        composable(AppRoutes.JoinSession) {
            val factory = remember(container) {
                viewModelFactory {
                    initializer {
                        JoinSessionViewModel(
                            authRepository = container.authRepository,
                            sessionRepository = container.sessionRepository,
                            connectivityRepository = container.connectivityRepository
                        )
                    }
                }
            }
            val viewModel: JoinSessionViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            JoinSessionScreen(
                uiState = uiState,
                onCodeChanged = viewModel::onCodeChanged,
                onJoin = viewModel::joinSession,
                onBack = { navController.popBackStack() },
                onNavigateToSession = { sessionId ->
                    navController.navigate(AppRoutes.sessionHome(sessionId)) {
                        popUpTo(AppRoutes.Sessions)
                    }
                },
                onNavigationConsumed = viewModel::navigationConsumed
            )
        }

        composable(
            route = AppRoutes.SessionHome,
            arguments = listOf(navArgument(AppRoutes.SessionIdArg) { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = requireNotNull(backStackEntry.arguments?.getString(AppRoutes.SessionIdArg))
            val factory = remember(container, sessionId) {
                viewModelFactory {
                    initializer {
                        SessionHomeViewModel(
                            sessionId = sessionId,
                            authRepository = container.authRepository,
                            sessionRepository = container.sessionRepository,
                            connectivityRepository = container.connectivityRepository
                        )
                    }
                }
            }
            val viewModel: SessionHomeViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            SessionHomeScreen(
                uiState = uiState,
                onOpenWheel = { navController.navigate(AppRoutes.sessionWheel(sessionId)) },
                onOpenLightning = { navController.navigate(AppRoutes.sessionLightning(sessionId)) },
                onOpenAdmin = { navController.navigate(AppRoutes.sessionAdmin(sessionId)) },
                onOpenHistory = { navController.navigate(AppRoutes.sessionHistory(sessionId)) },
                onOpenLocalSettings = { navController.navigate(AppRoutes.localSettings(sessionId)) },
                onOpenSettings = { navController.navigate(AppRoutes.sessionSettings(sessionId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoutes.SessionSettings,
            arguments = listOf(navArgument(AppRoutes.SessionIdArg) { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = requireNotNull(backStackEntry.arguments?.getString(AppRoutes.SessionIdArg))
            val factory = remember(container, sessionId) {
                viewModelFactory {
                    initializer {
                        SessionSettingsViewModel(
                            sessionId = sessionId,
                            authRepository = container.authRepository,
                            sessionRepository = container.sessionRepository,
                            connectivityRepository = container.connectivityRepository
                        )
                    }
                }
            }
            val viewModel: SessionSettingsViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            SessionSettingsScreen(
                uiState = uiState,
                onEventNameChanged = viewModel::onEventNameChanged,
                onGroomNameChanged = viewModel::onGroomNameChanged,
                onPhotoUrlChanged = viewModel::onPhotoUrlChanged,
                onPhotoLoaded = viewModel::onPhotoLoaded,
                onPhotoLoadFailed = viewModel::onPhotoLoadFailed,
                onStartsAtChanged = viewModel::onStartsAtChanged,
                onEndsAtChanged = viewModel::onEndsAtChanged,
                onTimeZoneChanged = viewModel::onTimeZoneChanged,
                onSave = viewModel::save,
                onRegenerateCode = viewModel::regenerateCode,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onNavigationConsumed = viewModel::navigationConsumed
            )
        }

        composable(
            route = AppRoutes.SessionWheel,
            arguments = listOf(navArgument(AppRoutes.SessionIdArg) { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = requireNotNull(backStackEntry.arguments?.getString(AppRoutes.SessionIdArg))
            val factory = remember(container, sessionId) {
                viewModelFactory {
                    initializer {
                        RouletteViewModel(
                            sessionId = sessionId,
                            authRepository = container.authRepository,
                            rouletteRepository = container.rouletteRepository,
                            connectivityRepository = container.connectivityRepository,
                            localSettingsRepository = container.localSettingsRepository
                        )
                    }
                }
            }
            val viewModel: RouletteViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            RouletteScreen(
                uiState = uiState,
                onSpinCategory = viewModel::spinCategory,
                onSpinContent = viewModel::spinContent,
                onResolveResult = viewModel::resolveResult,
                onResetGame = viewModel::resetGame,
                onOpenAdmin = { navController.navigate(AppRoutes.sessionAdmin(sessionId)) },
                onOpenHistory = { navController.navigate(AppRoutes.sessionHistory(sessionId)) },
                onOpenLocalSettings = { navController.navigate(AppRoutes.localSettings(sessionId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoutes.SessionLightning,
            arguments = listOf(navArgument(AppRoutes.SessionIdArg) { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = requireNotNull(backStackEntry.arguments?.getString(AppRoutes.SessionIdArg))
            val factory = remember(container, sessionId) {
                viewModelFactory {
                    initializer {
                        LightningViewModel(
                            sessionId = sessionId,
                            authRepository = container.authRepository,
                            rouletteRepository = container.rouletteRepository,
                            connectivityRepository = container.connectivityRepository
                        )
                    }
                }
            }
            val viewModel: LightningViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LightningScreen(
                uiState = uiState,
                onStartRound = viewModel::startRound,
                onAnswer = viewModel::answer,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoutes.SessionAdmin,
            arguments = listOf(navArgument(AppRoutes.SessionIdArg) { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = requireNotNull(backStackEntry.arguments?.getString(AppRoutes.SessionIdArg))
            val factory = remember(container, sessionId) {
                viewModelFactory {
                    initializer {
                        AdminViewModel(
                            sessionId = sessionId,
                            authRepository = container.authRepository,
                            rouletteRepository = container.rouletteRepository,
                            parser = container.contentImportParser,
                            connectivityRepository = container.connectivityRepository
                        )
                    }
                }
            }
            val viewModel: AdminViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            AdminScreen(
                uiState = uiState,
                onParseFile = viewModel::parseFile,
                onConfirmImport = viewModel::confirmImport,
                onLoadDemoContent = viewModel::loadDemoContent,
                onClearPreview = viewModel::clearPreview,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoutes.SessionHistory,
            arguments = listOf(navArgument(AppRoutes.SessionIdArg) { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = requireNotNull(backStackEntry.arguments?.getString(AppRoutes.SessionIdArg))
            val factory = remember(container, sessionId) {
                viewModelFactory {
                    initializer {
                        HistoryViewModel(
                            sessionId = sessionId,
                            authRepository = container.authRepository,
                            rouletteRepository = container.rouletteRepository,
                            sessionRepository = container.sessionRepository,
                            connectivityRepository = container.connectivityRepository
                        )
                    }
                }
            }
            val viewModel: HistoryViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            HistoryScreen(
                uiState = uiState,
                onRestore = viewModel::restore,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoutes.LocalSettings,
            arguments = listOf(navArgument(AppRoutes.SessionIdArg) { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = requireNotNull(backStackEntry.arguments?.getString(AppRoutes.SessionIdArg))
            val factory = remember(container, sessionId) {
                viewModelFactory {
                    initializer {
                        LocalSettingsViewModel(
                            sessionId = sessionId,
                            localSettingsRepository = container.localSettingsRepository,
                            notificationScheduler = container.notificationScheduler
                        )
                    }
                }
            }
            val viewModel: LocalSettingsViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LocalSettingsScreen(
                uiState = uiState,
                sessionId = sessionId,
                onSetThisSessionActive = viewModel::setThisSessionActive,
                onNotificationsChanged = viewModel::setNotificationsEnabled,
                onSoundChanged = viewModel::setSoundEnabled,
                onHapticChanged = viewModel::setHapticEnabled,
                onVisualEffectsChanged = viewModel::setVisualEffectsEnabled,
                onQuietStartChanged = viewModel::setQuietStart,
                onQuietEndChanged = viewModel::setQuietEnd,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
