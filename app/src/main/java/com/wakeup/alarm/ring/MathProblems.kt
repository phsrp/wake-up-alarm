package com.wakeup.alarm.ring

import com.wakeup.alarm.Difficulty
import kotlin.random.Random

data class MathProblem(val text: String, val answer: Int)

object MathGenerator {
    fun next(difficulty: Difficulty, random: Random = Random.Default): MathProblem = when (difficulty) {
        Difficulty.EASY -> {
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

        Difficulty.MEDIUM -> when (random.nextInt(3)) {
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

        Difficulty.HARD -> when (random.nextInt(2)) {
            0 -> {
                val a = random.nextInt(12, 30)
                val b = random.nextInt(11, 20)
                MathProblem("$a × $b", a * b)
            }

            else -> {
                val a = random.nextInt(6, 16)
                val b = random.nextInt(6, 16)
                val c = random.nextInt(20, 100)
                MathProblem("$a × $b + $c", a * b + c)
            }
        }
    }
}
