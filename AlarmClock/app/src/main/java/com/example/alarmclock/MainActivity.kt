package com.example.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var timePicker: TimePicker
    private lateinit var ringtoneText: TextView
    private lateinit var statusText: TextView
    private var selectedRingtoneUri: Uri? = null

    companion object {
        const val PREFS = "alarm_prefs"
        const val KEY_RINGTONE = "ringtone_uri"
        const val KEY_HOUR = "alarm_hour"
        const val KEY_MINUTE = "alarm_minute"
        const val RINGTONE_REQUEST_CODE = 1001
        const val ALARM_REQUEST_CODE = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        timePicker = findViewById(R.id.timePicker)
        timePicker.setIs24HourView(false)
        ringtoneText = findViewById(R.id.ringtoneText)
        statusText = findViewById(R.id.statusText)

        val chooseRingtoneButton: Button = findViewById(R.id.chooseRingtoneButton)
        val setAlarmButton: Button = findViewById(R.id.setAlarmButton)
        val cancelAlarmButton: Button = findViewById(R.id.cancelAlarmButton)

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedUriString = prefs.getString(KEY_RINGTONE, null)
        selectedRingtoneUri = if (savedUriString != null) {
            Uri.parse(savedUriString)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }
        ringtoneText.text = getRingtoneTitle(selectedRingtoneUri)

        val savedHour = prefs.getInt(KEY_HOUR, -1)
        val savedMinute = prefs.getInt(KEY_MINUTE, -1)
        if (savedHour >= 0) {
            statusText.text = "Alarm set for %02d:%02d".format(savedHour, savedMinute)
        }

        chooseRingtoneButton.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedRingtoneUri)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, RINGTONE_REQUEST_CODE)
        }

        setAlarmButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!am.canScheduleExactAlarms()) {
                    Toast.makeText(this, "Please allow exact alarms for this app", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    return@setOnClickListener
                }
            }
            setAlarm()
        }

        cancelAlarmButton.setOnClickListener {
            cancelAlarm()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RINGTONE_REQUEST_CODE && data != null) {
            @Suppress("DEPRECATION")
            val uri: Uri? = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                selectedRingtoneUri = uri
                ringtoneText.text = getRingtoneTitle(uri)
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_RINGTONE, uri.toString())
                    .apply()
            }
        }
    }

    private fun getRingtoneTitle(uri: Uri?): String {
        if (uri == null) return "None"
        return try {
            val ringtone = RingtoneManager.getRingtone(this, uri)
            ringtone?.getTitle(this) ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun setAlarm() {
        val hour = timePicker.hour
        val minute = timePicker.minute

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(this, AlarmReceiver::class.java)
        intent.putExtra("ringtone_uri", selectedRingtoneUri?.toString())

        val pendingIntent = PendingIntent.getBroadcast(
            this, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent),
            pendingIntent
        )

        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()

        statusText.text = "Alarm set for %02d:%02d".format(hour, minute)
        Toast.makeText(this, "Alarm set", Toast.LENGTH_SHORT).show()
    }

    private fun cancelAlarm() {
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent)
        statusText.text = "No alarm set"
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_HOUR)
            .remove(KEY_MINUTE)
            .apply()
        Toast.makeText(this, "Alarm cancelled", Toast.LENGTH_SHORT).show()
    }
}
