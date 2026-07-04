package com.example.despedidaruleta.feature.admin

import com.example.despedidaruleta.domain.model.ImportRow
import com.example.despedidaruleta.domain.model.RouletteCategory

object DemoRouletteContent {
    fun rows(): List<ImportRow> = buildList {
        addAll(questionRows())
        addAll(challengeRows())
        addAll(lightningRows())
        addAll(punishmentRows())
    }

    private fun questionRows(): List<ImportRow> = listOf(
        "Cual fue la primera impresion que tuviste del novio?",
        "Que historia del novio no puede faltar hoy?",
        "Que cancion define mejor al novio cuando sale de fiesta?",
        "Que consejo absurdo pero util le darias para la boda?",
        "Quien del grupo sobreviviria mejor a un viaje improvisado con el novio?",
        "Cual ha sido el plan mas raro que el novio ha aceptado sin preguntar?",
        "Que frase tipica del novio deberia estar prohibida esta noche?",
        "Que superpoder tendria el novio en una pelicula de colegas?",
        "Que objeto resume mejor la personalidad del novio?",
        "Que mentira piadosa le hemos perdonado todos alguna vez?",
        "Quien conoce el secreto mas ridiculo del novio?",
        "Que titular de periodico describiria esta despedida?"
    ).mapIndexed { index, text -> demoRow(RouletteCategory.QUESTION, index + 1, text) }

    private fun challengeRows(): List<ImportRow> = listOf(
        "Haz un brindis de 20 segundos como si fueras presentador de gala.",
        "Imita al novio entrando tarde a una reunion importante.",
        "Consigue que tres personas del grupo canten el estribillo de una cancion elegida por ti.",
        "Cuenta una anecdota del novio usando solo palabras de una silaba durante 30 segundos.",
        "Haz una pose de portada de revista con el novio y otro invitado.",
        "Convence al grupo de que eres el mejor padrino en menos de un minuto.",
        "Di tres virtudes del novio y una mejora pendiente sin reirte.",
        "Haz de comentarista deportivo narrando el proximo giro de la ruleta.",
        "Elige a alguien para recrear una escena dramatica de telenovela con el novio.",
        "Inventa un lema oficial para la despedida y haz que todos lo repitan."
    ).mapIndexed { index, text -> demoRow(RouletteCategory.CHALLENGE, index + 1, text) }

    private fun lightningRows(): List<ImportRow> = listOf(
        "Comida favorita de tu pareja?",
        "Fecha exacta de vuestro primer beso?",
        "Numero de calzado de tu pareja?",
        "Destino sonado de tu pareja para la luna de miel?",
        "Cancion que pondria tu pareja en la boda?",
        "Nombre completo de tu suegra?",
        "Que le molesta mas a tu pareja de ti?",
        "Color favorito de tu pareja?",
        "Primer regalo que le hiciste a tu pareja?",
        "Serie que tu pareja ha visto mas veces?"
    ).mapIndexed { index, text -> demoRow(RouletteCategory.LIGHTNING, index + 1, text) }

    private fun punishmentRows(): List<ImportRow> = listOf(
        "Durante la siguiente ronda solo puedes hablar como si fueras un mayordomo serio.",
        "Pierdes el derecho a elegir musica durante 10 minutos.",
        "Tienes que llamar al novio por un titulo inventado hasta el proximo giro.",
        "Haz una reverencia exagerada cada vez que alguien diga la palabra boda durante 5 minutos.",
        "El grupo elige un apodo temporal para ti hasta el proximo giro.",
        "Debes responder la proxima pregunta como si estuvieras en una rueda de prensa.",
        "Tienes que hacer una mini coreografia de 10 segundos antes del siguiente giro.",
        "Durante un turno, solo puedes celebrar con aplausos muy elegantes."
    ).mapIndexed { index, text -> demoRow(RouletteCategory.PUNISHMENT, index + 1, text) }

    private fun demoRow(category: RouletteCategory, number: Int, text: String): ImportRow = ImportRow(
        sourceRow = number,
        category = category,
        number = number,
        text = text
    )
}
