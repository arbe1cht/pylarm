package ru.pylarm

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

class ChallengeActivity : Activity() {
    private var solved = 0
    private var answer = 0
    private lateinit var progress: TextView
    private lateinit var question: TextView
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        showQuestionScreen()
    }

    private fun showQuestionScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 48, 32, 48)
            setBackgroundColor(Color.rgb(25, 22, 32))
        }
        progress = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        question = TextView(this).apply {
            textSize = 38f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 30)
        }
        input = EditText(this).apply {
            hint = "Ответ"
            textSize = 24f
            gravity = Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val submit = Button(this).apply {
            text = "Проверить"
            setOnClickListener { checkAnswer() }
        }
        root.addView(progress)
        root.addView(question)
        root.addView(input, LinearLayout.LayoutParams(-1, -2))
        root.addView(submit, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 24 })
        setContentView(root)
        nextQuestion()
    }

    private fun nextQuestion() {
        val left = Random.nextInt(2, 51)
        val right = Random.nextInt(2, 51)
        when (Random.nextInt(4)) {
            0 -> { answer = left + right; question.text = "$left + $right = ?" }
            1 -> { answer = left - right; question.text = "$left − $right = ?" }
            2 -> {
                val a = Random.nextInt(2, 13)
                val b = Random.nextInt(2, 13)
                answer = a * b
                question.text = "$a × $b = ?"
            }
            else -> {
                val divisor = Random.nextInt(2, 13)
                val quotient = Random.nextInt(2, 13)
                answer = quotient
                question.text = "${divisor * quotient} ÷ $divisor = ?"
            }
        }
        progress.text = "Решено: $solved из 10"
        input.text.clear()
    }

    private fun checkAnswer() {
        if (input.text.toString().toIntOrNull() != answer) {
            input.error = "Неверно — попробуйте ещё раз"
            return
        }
        solved++
        if (solved == 10) {
            startService(Intent(this, AlarmRingingService::class.java).setAction(AlarmRingingService.ACTION_STOP))
            Toast.makeText(this, "Будильник выключен. Доброе утро!", Toast.LENGTH_LONG).show()
            finish()
        } else {
            nextQuestion()
        }
    }
}
