package com.example.despedidaruleta.domain.model

class CodeAlreadyReservedException : Exception("El codigo ya estaba reservado.")
class InvalidJoinCodeException : Exception("Ese codigo no existe o ya no esta activo.")
class SessionNotActiveException : Exception("La sesion no esta activa.")
class SessionNotFoundException : Exception("No se ha encontrado la sesion.")
class SessionJoinLimitException : Exception("No se pudo reservar un codigo unico. Intentalo de nuevo.")
class RouletteContentMissingException : Exception("Importa contenido antes de girar la ruleta.")
class RouletteExhaustedException : Exception("Ya no queda contenido disponible en la ruleta.")
class SpinNotFoundException : Exception("No se ha encontrado el giro.")
class SpinAlreadyRestoredException : Exception("Ese giro ya estaba restaurado.")
class ImportPreviewEmptyException : Exception("El archivo no tiene filas validas para importar.")
