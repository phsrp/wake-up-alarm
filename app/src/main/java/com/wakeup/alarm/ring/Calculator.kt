package com.wakeup.alarm.ring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.floor

/** Tiny calculator maths: + − × ÷ on whole numbers, with × and ÷ done first. */
object CalcEngine {
    private val operators = setOf('+', '−', '×', '÷')

    /** Returns the answer as text, or null if the expression is incomplete or divides by zero. */
    fun evaluate(expression: String): String? {
        val tokens = mutableListOf<String>()
        val number = StringBuilder()
        for (char in expression) {
            if (char.isDigit()) {
                number.append(char)
            } else if (char in operators) {
                if (number.isEmpty()) return null
                tokens += number.toString()
                number.clear()
                tokens += char.toString()
            } else {
                return null
            }
        }
        if (number.isEmpty()) return null
        tokens += number.toString()

        val values = mutableListOf(tokens[0].toDouble())
        val pending = mutableListOf<Char>()
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i][0]
            val right = tokens[i + 1].toDouble()
            when (op) {
                '×' -> values[values.lastIndex] = values.last() * right
                '÷' -> {
                    if (right == 0.0) return null
                    values[values.lastIndex] = values.last() / right
                }

                else -> {
                    pending += op
                    values += right
                }
            }
            i += 2
        }

        var result = values[0]
        pending.forEachIndexed { index, op ->
            result = if (op == '+') result + values[index + 1] else result - values[index + 1]
        }
        return if (result == floor(result) && abs(result) < 1e15) result.toLong().toString() else "%.2f".format(result)
    }
}

/** Full-screen calculator, unlocked after nine wrong answers. The problem underneath is kept. */
@Composable
fun CalculatorOverlay(onClose: () -> Unit) {
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }

    fun press(key: String) {
        when (key) {
            "C" -> {
                expression = ""
                result = null
            }

            "⌫" -> {
                expression = expression.dropLast(1)
                result = null
            }

            "=" -> {
                val answer = CalcEngine.evaluate(expression)
                result = answer ?: "Error"
            }

            "+", "−", "×", "÷" -> {
                // Carry on from a finished answer, otherwise just add the operator.
                val shown = result
                if (shown != null && shown != "Error") expression = shown
                result = null
                if (expression.isNotEmpty() && !expression.last().isOperator()) expression += key
            }

            else -> {
                if (result != null) {
                    expression = ""
                    result = null
                }
                if (expression.length < 24) expression += key
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Calculator",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        expression.ifEmpty { "0" },
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                    )
                    Text(
                        result?.let { "= $it" } ?: "",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                    )
                }
            }

            val rows = listOf(
                listOf("C", "⌫", "÷", "×"),
                listOf("7", "8", "9", "−"),
                listOf("4", "5", "6", "+"),
                listOf("1", "2", "3", "="),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { key ->
                            CalcKey(key, Modifier.weight(1f)) { press(key) }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CalcKey("0", Modifier.weight(1f)) { press("0") }
                    Button(
                        onClick = onClose,
                        modifier = Modifier
                            .weight(3f)
                            .fillMaxHeight(),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Text("Back to the problem", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun Char.isOperator() = this == '+' || this == '−' || this == '×' || this == '÷'

@Composable
private fun CalcKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = MaterialTheme.shapes.large,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
    }
}
