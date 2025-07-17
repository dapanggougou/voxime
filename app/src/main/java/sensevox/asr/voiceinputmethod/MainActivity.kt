// === VoiceInputMethod/app/src/main/java/com/yourname/voiceinputmethod/MainActivity.kt ===
package sensevox.asr.voiceinputmethod

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val prefsName = "settings"
    private val keyAsrUrl = "asr_url"
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- 创建界面 ---
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val title = TextView(this).apply {
            text = "设置"
            textSize = 22f
        }

        val description = TextView(this).apply {
            text = "\n请输入你的 ASR 服务地址，例如：\nhttp://192.168.1.10:8000/asr"
            textSize = 14f
        }

        val urlInput = EditText(this).apply {
            hint = "http://..."
            setText(loadUrl())
        }

        val saveButton = Button(this).apply {
            text = "保存地址"
            setOnClickListener {
                val urlToSave = urlInput.text.toString().trim()
                if (urlToSave.startsWith("http")) {
                    saveUrl(urlToSave)
                    Toast.makeText(this@MainActivity, "保存成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "请输入有效的URL", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 新增：检测输入法是否开启的按钮
        val checkEnabledButton = Button(this).apply {
            text = "检测输入法是否开启"
            setOnClickListener {
                checkInputMethodEnabled()
            }
        }

        // 新增：检测输入法是否激活的按钮
        val checkActiveButton = Button(this).apply {
            text = "检测输入法是否激活"
            setOnClickListener {
                checkInputMethodActive()
            }
        }

        val footer = TextView(this).apply {
            text = ""
        }

        // 将所有控件添加到布局中
        layout.addView(title)
        layout.addView(description)
        layout.addView(urlInput)
        layout.addView(saveButton)
        layout.addView(checkEnabledButton)
        layout.addView(checkActiveButton)
        layout.addView(footer)

        setContentView(layout)
        checkAndRequestPermission()
    }

    private fun saveUrl(url: String) {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().putString(keyAsrUrl, url).apply()
    }

    private fun loadUrl(): String {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        return prefs.getString(keyAsrUrl, "") ?: ""
    }

    // 检测输入法是否开启
    private fun checkInputMethodEnabled() {
        val packageName = packageName
        val enabledInputMethods = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_INPUT_METHODS
        )

        val isEnabled = enabledInputMethods?.contains(packageName) == true

        if (isEnabled) {
            Toast.makeText(this, "输入法已开启", Toast.LENGTH_SHORT).show()
        } else {
            showEnableInputMethodDialog()
        }
    }

    // 检测输入法是否激活（当前使用）
    private fun checkInputMethodActive() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentInputMethod = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )

        val packageName = packageName
        val isActive = currentInputMethod?.contains(packageName) == true

        if (isActive) {
            Toast.makeText(this, "输入法已激活", Toast.LENGTH_SHORT).show()
        } else {
            showActivateInputMethodDialog()
        }
    }

    // 显示开启输入法的对话框
    private fun showEnableInputMethodDialog() {
        AlertDialog.Builder(this)
            .setTitle("输入法未开启")
            .setMessage("您的输入法尚未开启，是否前往设置页面开启？")
            .setPositiveButton("去开启") { _, _ ->
                // 跳转到输入法设置页面
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // 显示激活输入法的对话框
    private fun showActivateInputMethodDialog() {
        AlertDialog.Builder(this)
            .setTitle("输入法未激活")
            .setMessage("您的输入法尚未激活，是否前往设置页面激活？")
            .setPositiveButton("去激活") { _, _ ->
                // 跳转到输入法选择页面
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun checkAndRequestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "录音权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "权限被拒绝，语音输入功能将无法使用", Toast.LENGTH_LONG).show()
            }
        }
    }
}
