package com.mipatrimonio.app.domain.model

import java.time.LocalDate

enum class MovementStatus { EJECUTADO, PREVISTO }

fun movementStatus(date: LocalDate, today: LocalDate): MovementStatus =
    if (date.isAfter(today)) MovementStatus.PREVISTO else MovementStatus.EJECUTADO
