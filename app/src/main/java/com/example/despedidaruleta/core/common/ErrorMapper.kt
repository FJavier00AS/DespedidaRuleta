package com.example.despedidaruleta.core.common

import com.example.despedidaruleta.domain.model.InvalidJoinCodeException
import com.example.despedidaruleta.domain.model.ImportPreviewEmptyException
import com.example.despedidaruleta.domain.model.RouletteContentMissingException
import com.example.despedidaruleta.domain.model.RouletteExhaustedException
import com.example.despedidaruleta.domain.model.SpinAlreadyRestoredException
import com.example.despedidaruleta.domain.model.SpinNotFoundException
import com.example.despedidaruleta.domain.model.SessionJoinLimitException
import com.example.despedidaruleta.domain.model.SessionNotActiveException
import com.example.despedidaruleta.domain.model.SessionNotFoundException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException

fun Throwable.toUserMessage(): String = when (this) {
    is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo."
    is FirebaseAuthWeakPasswordException -> "La contrasena es demasiado debil. Usa al menos 6 caracteres."
    is FirebaseAuthInvalidCredentialsException -> "Correo o contrasena no validos."
    is FirebaseAuthInvalidUserException -> "No existe una cuenta activa con ese correo."
    is FirebaseNetworkException -> "No hay conexion. Revisa la red e intentalo de nuevo."
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Firestore ha rechazado la operacion. Revisa que las reglas de seguridad de esta fase esten desplegadas."
        FirebaseFirestoreException.Code.UNAVAILABLE -> if (mentionsUnknownHost()) {
            "No se puede conectar con Firestore. El dispositivo no resuelve firestore.googleapis.com; revisa internet, DNS o el emulador."
        } else {
            "Firestore no esta disponible ahora mismo. Revisa la conexion e intentalo de nuevo."
        }
        else -> localizedMessage ?: "Error de sincronizacion con Firestore."
    }
    is InvalidJoinCodeException -> message ?: "Codigo no valido."
    is SessionNotActiveException -> message ?: "La sesion no esta activa."
    is SessionNotFoundException -> message ?: "Sesion no encontrada."
    is SessionJoinLimitException -> message ?: "No se pudo crear la sesion."
    is RouletteContentMissingException -> message ?: "Falta contenido."
    is RouletteExhaustedException -> message ?: "Ruleta agotada."
    is SpinNotFoundException -> message ?: "Giro no encontrado."
    is SpinAlreadyRestoredException -> message ?: "Giro ya restaurado."
    is ImportPreviewEmptyException -> message ?: "Archivo sin filas validas."
    else -> localizedMessage ?: "Ha ocurrido un error inesperado."
}

private fun Throwable.mentionsUnknownHost(): Boolean {
    return generateSequence(this) { it.cause }
        .mapNotNull { it.message }
        .any { message ->
            message.contains("UnknownHostException", ignoreCase = true) ||
                message.contains("Unable to resolve host", ignoreCase = true) ||
                message.contains("firestore.googleapis.com", ignoreCase = true)
        }
}
