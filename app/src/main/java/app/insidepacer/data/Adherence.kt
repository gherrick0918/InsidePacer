package app.insidepacer.data

import app.insidepacer.domain.Program

fun dayIndexFor(program: Program, epochDay: Long): Int =
    (epochDay - program.startEpochDay).toInt()

fun inRange(program: Program, idx: Int): Boolean =
    idx >= 0 && idx < program.weeks * program.daysPerWeek
