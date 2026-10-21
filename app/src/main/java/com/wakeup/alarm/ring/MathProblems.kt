package com.wakeup.alarm.ring

import com.wakeup.alarm.Difficulty
import kotlin.random.Random

data class MathProblem(val text: String, val answer: Int)

/**
 * Every level is meant to be solvable in your head while half asleep:
 * Easy is single digits and small teens, Medium is two-digit adding and taking away,
 * Hard adds times tables and three-digit sums.
 */
object MathGenerator {
    fun next(difficulty: Difficulty, random: Random = Random.Default): MathProblem = when (difficulty) {
        Difficulty.EASY -> {
            if (random.nextBoolean()) {
                val a = random.nextInt(3, 10)
                val b = random.nextInt(3, 10)
                MathProblem("$a + $b", a + b)
            } else {
                val a = random.nextInt(11, 19)
                val b = random.nextInt(3, 10)
                MathProblem("$a − $b", a - b)
            }
        }

        Difficulty.MEDIUM -> {
            val a = random.nextInt(10, 60)
            val b = random.nextInt(10, 40)
            if (random.nextBoolean()) {
                MathProblem("$a + $b", a + b)
            } else {
                val big = maxOf(a, b)
                val small = minOf(a, b)
                MathProblem("$big − $small", big - small)
            }
        }

        Difficulty.HARD -> when (random.nextInt(3)) {
            0 -> {
                val a = random.nextInt(6, 20)
                val b = random.nextInt(3, 10)
                MathProblem("$a × $b", a * b)
            }

            1 -> {
                val a = random.nextInt(100, 500)
                val b = random.nextInt(100, 500)
                MathProblem("$a + $b", a + b)
            }

            else -> {
                val a = random.nextInt(300, 900)
                val b = random.nextInt(20, 99)
                MathProblem("$a − $b", a - b)
            }
        }
    }
}
