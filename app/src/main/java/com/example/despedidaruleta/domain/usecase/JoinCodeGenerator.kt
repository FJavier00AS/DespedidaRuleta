package com.example.despedidaruleta.domain.usecase

import kotlin.random.Random

class JoinCodeGenerator(
    private val random: Random = Random.Default
) {
    fun generate(): String = random.nextInt(from = 0, until = MAX_CODE_EXCLUSIVE)
        .toString()
        .padStart(CODE_LENGTH, '0')

    companion object {
        const val CODE_LENGTH = 6
        private const val MAX_CODE_EXCLUSIVE = 1_000_000
    }
}
