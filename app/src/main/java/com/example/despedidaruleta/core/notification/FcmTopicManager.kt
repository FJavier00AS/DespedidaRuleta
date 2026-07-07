package com.example.despedidaruleta.core.notification

import com.example.despedidaruleta.core.firebase.await
import com.google.firebase.messaging.FirebaseMessaging

class FcmTopicManager {
    suspend fun subscribe(sessionId: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(topicName(sessionId)).await()
    }

    suspend fun unsubscribe(sessionId: String) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topicName(sessionId)).await()
    }

    private fun topicName(sessionId: String): String = "session_$sessionId"
}
