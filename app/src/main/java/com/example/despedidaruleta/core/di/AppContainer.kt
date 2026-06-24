package com.example.despedidaruleta.core.di

import android.content.Context
import com.example.despedidaruleta.core.connectivity.AndroidConnectivityRepository
import com.example.despedidaruleta.core.notification.AndroidNotificationScheduler
import com.example.despedidaruleta.core.notification.NotificationScheduler
import com.example.despedidaruleta.data.auth.FirebaseAuthRepository
import com.example.despedidaruleta.data.importer.XlsxContentImportParser
import com.example.despedidaruleta.data.roulette.FirebaseRouletteRepository
import com.example.despedidaruleta.data.session.FirebaseSessionRepository
import com.example.despedidaruleta.data.settings.DataStoreLocalSettingsRepository
import com.example.despedidaruleta.domain.repository.AuthRepository
import com.example.despedidaruleta.domain.repository.ContentImportParser
import com.example.despedidaruleta.domain.repository.ConnectivityRepository
import com.example.despedidaruleta.domain.repository.LocalSettingsRepository
import com.example.despedidaruleta.domain.repository.RouletteRepository
import com.example.despedidaruleta.domain.repository.SessionRepository
import com.example.despedidaruleta.domain.usecase.JoinCodeGenerator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AppContainer(context: Context) {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val authRepository: AuthRepository = FirebaseAuthRepository(auth, firestore)
    val sessionRepository: SessionRepository = FirebaseSessionRepository(firestore, JoinCodeGenerator())
    val rouletteRepository: RouletteRepository = FirebaseRouletteRepository(firestore)
    val connectivityRepository: ConnectivityRepository = AndroidConnectivityRepository(context.applicationContext)
    val contentImportParser: ContentImportParser = XlsxContentImportParser(context.applicationContext)
    val localSettingsRepository: LocalSettingsRepository = DataStoreLocalSettingsRepository(context.applicationContext)
    val notificationScheduler: NotificationScheduler = AndroidNotificationScheduler(context.applicationContext)
}
