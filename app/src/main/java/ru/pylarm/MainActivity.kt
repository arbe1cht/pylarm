package ru.pylarm

import android.app.Activity
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity() {
    private val handler = android.os.Handler(mainLooper)
    private lateinit var alarmList: LinearLayout
    private lateinit var dateButton: Button
    private lateinit var melodyButton: Button
    private lateinit var weekdays: List<CheckBox>
    private var selectedDate: String? = null
    private var ringtoneUri: String? = null
    private var ringtoneTitle = "Мелодия по умолчанию"
    private val clockViews = mutableListOf<Pair<TextView, TimeZone>>()
    private val clockTick = object : Runnable {
        override fun run() {
            val now = Date()
            val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            findViewById<TextView>(CURRENT_TIME_ID)?.text = format.format(now)
            clockViews.forEach { (view, zone) ->
                val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply { timeZone = zone }
                view.text = clockFormat.format(now)
            }
            handler.postDelayed(this, 40L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        renderAlarms()
        handler.post(clockTick)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::alarmList.isInitialized) {
            renderAlarms()
            AlarmScheduler.rescheduleAll(this)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(clockTick)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_RINGTONE && resultCode == RESULT_OK) {
            val uri: Uri? = data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ringtoneUri = uri?.toString()
            ringtoneTitle = uri?.let { RingtoneManager.getRingtone(this, it)?.getTitle(this) }
                ?: "Мелодия по умолчанию"
            melodyButton.text = "Мелодия: $ringtoneTitle"
        }
    }

    private fun buildScreen() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 24)
        }
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)

        content.addView(label("ПЫЛБУДИЛЬНИК", 25f, true))
        content.addView(label("Точное отображение времени", 15f))
        content.addView(TextView(this).apply {
            id = CURRENT_TIME_ID
            text = "--:--:--.---"
            textSize = 34f
            setPadding(0, 4, 0, 16)
        })

        content.addView(label("МИРОВЫЕ ЧАСЫ", 18f, true))
        val places = listOf(
            "Москва" to "Europe/Moscow",
            "Лондон" to "Europe/London",
            "Нью-Йорк" to "America/New_York",
            "Токио" to "Asia/Tokyo",
            "Сидней" to "Australia/Sydney"
        )
        places.forEach { (city, zoneId) ->
            val zone = TimeZone.getTimeZone(zoneId)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(label(city, 16f), LinearLayout.LayoutParams(0, -2, 1f))
            val time = label("--:--:--", 16f, true)
            clockViews.add(time to zone)
            row.addView(time)
            content.addView(row)
        }

        content.addView(sectionSpace())
        content.addView(label("НОВЫЙ БУДИЛЬНИК", 18f, true))
        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val hour = numberInput("ЧЧ", 23)
        val minute = numberInput("ММ", 59)
        val second = numberInput("СС", 59)
        val millisecond = numberInput("мс", 999)
        listOf(hour, minute, second, millisecond).forEachIndexed { index, field ->
            timeRow.addView(field, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                marginEnd = if (index == 3) 0 else dp(8)
            })
        }
        content.addView(timeRow)

        dateButton = Button(this).apply {
            text = "Выбрать дату (необязательно)"
            setOnClickListener { chooseDate() }
        }
        content.addView(dateButton, matchWidth())

        content.addView(label("Повторять по дням недели (если дата не выбрана)", 14f))
        val dayLabels = listOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
        val dayValues = listOf(
            Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
        )
        val dayRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        weekdays = dayLabels.mapIndexed { index, day ->
            CheckBox(this).apply {
                text = day
                textSize = 12f
                setPadding(0, 0, 0, 0)
                tag = dayValues[index]
            }
        }
        weekdays.forEach { dayRow.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        content.addView(dayRow)

        melodyButton = Button(this).apply {
            text = "Мелодия: $ringtoneTitle"
            setOnClickListener { chooseRingtone() }
        }
        content.addView(melodyButton, matchWidth())
        content.addView(Button(this).apply {
            text = "Добавить будильник"
            setOnClickListener {
                val values = listOf(hour, minute, second, millisecond).map { it.text.toString().toIntOrNull() }
                if (values.any { it == null }) {
                    Toast.makeText(this@MainActivity, "Заполните время числами", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val (h, m, s, ms) = values.filterNotNull()
                if (h !in 0..23 || m !in 0..59 || s !in 0..59 || ms !in 0..999) {
                    Toast.makeText(this@MainActivity, "Проверьте диапазоны времени", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val saved = AlarmStore.get(this@MainActivity)
                val alarm = Alarm(
                    id = (saved.maxOfOrNull { it.id } ?: 0L) + 1L,
                    hour = h,
                    minute = m,
                    second = s,
                    millisecond = ms,
                    date = selectedDate,
                    weekdays = if (selectedDate == null) weekdays.filter { it.isChecked }.map { it.tag as Int }.toSet() else emptySet(),
                    ringtoneUri = ringtoneUri,
                    ringtoneTitle = ringtoneTitle
                )
                if (selectedDate != null && !isInFuture(alarm)) {
                    Toast.makeText(this@MainActivity, "Выбранное время уже прошло", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (alarm.date == null && alarm.weekdays.isEmpty() && !isInFutureToday(alarm)) {
                    Toast.makeText(this@MainActivity, "Укажите дату или дни недели", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                AlarmStore.save(this@MainActivity, saved + alarm)
                if (!AlarmScheduler.schedule(this@MainActivity, alarm)) {
                    askForExactAlarmPermission()
                } else {
                    Toast.makeText(this@MainActivity, "Будильник добавлен", Toast.LENGTH_SHORT).show()
                }
                selectedDate = null
                dateButton.text = "Выбрать дату (необязательно)"
                weekdays.forEach { it.isChecked = false }
                renderAlarms()
            }
        }, matchWidth())

        content.addView(sectionSpace())
        content.addView(label("БУДИЛЬНИКИ", 18f, true))
        alarmList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(alarmList, matchWidth())
        content.addView(label("Внимание: Android может задерживать срабатывание; миллисекунды — точность отображения, а не гарантия запуска ОС.", 12f))
    }

    private fun renderAlarms() {
        alarmList.removeAllViews()
        AlarmStore.get(this).sortedWith(compareBy<Alarm> { it.hour }.thenBy { it.minute }).forEach { alarm ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(4), dp(8))
            }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(label(String.format(Locale.getDefault(), "%02d:%02d:%02d.%03d", alarm.hour, alarm.minute, alarm.second, alarm.millisecond), 22f, true))
            val repeat = when {
                alarm.date != null -> alarm.date
                alarm.weekdays.isEmpty() -> "Один раз"
                else -> alarm.weekdays.sorted().joinToString(" ") { weekdayName(it) }
            }
            info.addView(label("$repeat • ${alarm.ringtoneTitle}", 12f))
            card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(Switch(this).apply {
                isChecked = alarm.enabled
                setOnCheckedChangeListener { _, checked ->
                    val updated = alarm.copy(enabled = checked)
                    AlarmStore.save(this@MainActivity, AlarmStore.get(this@MainActivity).map {
                        if (it.id == alarm.id) updated else it
                    })
                    if (checked && !AlarmScheduler.schedule(this@MainActivity, updated)) askForExactAlarmPermission()
                    if (!checked) AlarmScheduler.cancel(this@MainActivity, alarm.id)
                }
            })
            card.addView(Button(this).apply {
                text = "Удалить"
                setOnClickListener {
                    AlarmScheduler.cancel(this@MainActivity, alarm.id)
                    AlarmStore.save(this@MainActivity, AlarmStore.get(this@MainActivity).filterNot { it.id == alarm.id })
                    renderAlarms()
                }
            })
            alarmList.addView(card)
        }
    }

    private fun chooseDate() {
        val initial = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            selectedDate = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day)
            dateButton.text = "Дата: $selectedDate"
            weekdays.forEach { it.isChecked = false }
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }

    private fun chooseRingtone() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Выберите мелодию будильника")
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri?.let(Uri::parse))
        }
        startActivityForResult(intent, REQUEST_RINGTONE)
    }

    private fun askForExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Toast.makeText(this, "Разрешите точные будильники для приложения", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:$packageName"))
            )
        } else {
            Toast.makeText(this, "Не удалось запланировать будильник", Toast.LENGTH_LONG).show()
        }
    }

    private fun isInFutureToday(alarm: Alarm): Boolean {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, alarm.second)
            set(Calendar.MILLISECOND, alarm.millisecond)
        }
        return target.timeInMillis > now.timeInMillis
    }

    private fun isInFuture(alarm: Alarm): Boolean {
        val date = alarm.date ?: return isInFutureToday(alarm)
        val parts = date.split("-").map { it.toInt() }
        val target = Calendar.getInstance().apply {
            set(parts[0], parts[1] - 1, parts[2], alarm.hour, alarm.minute, alarm.second)
            set(Calendar.MILLISECOND, alarm.millisecond)
        }
        return target.timeInMillis > System.currentTimeMillis()
    }

    private fun weekdayName(day: Int) = when (day) {
        Calendar.SUNDAY -> "Вс"
        Calendar.MONDAY -> "Пн"
        Calendar.TUESDAY -> "Вт"
        Calendar.WEDNESDAY -> "Ср"
        Calendar.THURSDAY -> "Чт"
        Calendar.FRIDAY -> "Пт"
        else -> "Сб"
    }

    private fun numberInput(hintText: String, max: Int) = EditText(this).apply {
        hint = hintText
        textSize = 18f
        gravity = Gravity.CENTER
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(android.text.InputFilter.LengthFilter(if (max == 999) 3 else 2))
        setText(if (max == 999) "000" else "00")
    }

    private fun label(textValue: String, size: Float, bold: Boolean = false) =
        TextView(this).apply {
            text = textValue
            textSize = size
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), dp(4), dp(4))
        }

    private fun sectionSpace() = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(16)) }
    private fun matchWidth() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_RINGTONE = 1
        private const val CURRENT_TIME_ID = 101
    }
}
