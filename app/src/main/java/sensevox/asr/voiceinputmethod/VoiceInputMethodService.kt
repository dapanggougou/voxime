package sensevox.asr.voiceinputmethod

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.content.res.ColorStateList
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView


class VoiceInputMethodService : InputMethodService() {

    // --- 键盘状态管理 ---
    private enum class KeyboardState {
        VOICE, EMOJI, SYMBOLS, LATIN
    }

    private var currentState: KeyboardState = KeyboardState.VOICE

    // --- 视图缓存 ---
    private var mainContainer: LinearLayout? = null // 主容器，用于切换视图
    private var voiceInputView: View? = null
    private var emojiKeyboardView: View? = null
    private var symbolKeyboardView: View? = null
    private var latinKeyboardView: View? = null  // 新增拉丁字母键盘视图缓存

    // --- 语音和光标移动相关变量 ---
    private var initialX = 0f
    private var initialY = 0f
    private var isMovingCursor = false
    private var isRecording = false
    private val horizontalMovementThreshold = 30f
    private val verticalMovementThreshold = 65f
    private val recordingDelayTime = 100L

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var asrClient: ASRClient
    private lateinit var voiceButton: ImageButton
    private val mainHandler = Handler(Looper.getMainLooper())
    private var asrUrl: String = ""

    // --- 颜色定义 ---
    private val LIGHT_MODE_BG_COLOR = Color.rgb(235, 239, 242)
    private val DARK_MODE_BG_COLOR = Color.rgb(26, 12, 37)
    private val ICON_COLOR_GRAY = Color.rgb(150, 150, 150)

    override fun onCreate() {
        super.onCreate()
        audioRecorder = AudioRecorder(this)
        asrClient = ASRClient()
        loadUrl()
    }

    private fun loadUrl() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        asrUrl = prefs.getString("asr_url", "") ?: ""
    }

    override fun onWindowShown() {
        super.onWindowShown()
        loadUrl()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // 清理视图缓存，强制重新创建以适应新的屏幕方向
        clearViewCache()

        // 重新创建当前状态的视图
        switchKeyboardView(currentState)

        updateBackgroundAndIconColors(newConfig)
    }

    private fun clearViewCache() {
        voiceInputView = null
        emojiKeyboardView = null
        symbolKeyboardView = null
        latinKeyboardView = null
    }

    private fun updateBackgroundAndIconColors(config: Configuration) {
        val isDarkMode =
            (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isDarkMode) DARK_MODE_BG_COLOR else LIGHT_MODE_BG_COLOR

        // 更新所有视图的背景色，添加空值检查
        mainContainer?.setBackgroundColor(bgColor)
        voiceInputView?.setBackgroundColor(bgColor)
        emojiKeyboardView?.setBackgroundColor(bgColor)
        symbolKeyboardView?.setBackgroundColor(bgColor)
        latinKeyboardView?.setBackgroundColor(bgColor)
    }


    /**
     * 主入口，创建输入法视图的容器
     */
    override fun onCreateInputView(): View {
        mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 初始化时显示主语音键盘
        switchKeyboardView(KeyboardState.VOICE)

        // 初始化时根据当前模式设置背景色
        updateBackgroundAndIconColors(resources.configuration)

        return mainContainer!!
    }

    /**
     * 切换键盘视图的核心方法
     */
    private fun switchKeyboardView(newState: KeyboardState) {
        currentState = newState

        // 安全地移除所有视图
        try {
            mainContainer?.removeAllViews()
        } catch (e: Exception) {
            // 忽略可能的异常
        }

        val viewToShow = when (newState) {
            KeyboardState.VOICE -> {
                if (voiceInputView == null) {
                    voiceInputView = createVoiceInputView()
                }
                voiceInputView
            }

            KeyboardState.EMOJI -> {
                if (emojiKeyboardView == null) {
                    emojiKeyboardView = createEmojiKeyboardView()
                }
                emojiKeyboardView
            }

            KeyboardState.SYMBOLS -> {
                if (symbolKeyboardView == null) {
                    symbolKeyboardView = createSymbolKeyboardView()
                }
                symbolKeyboardView
            }

            KeyboardState.LATIN -> {
                if (latinKeyboardView == null) {
                    latinKeyboardView = createLatinKeyboardView()
                }
                latinKeyboardView
            }
        }

        viewToShow?.let { view ->
            // 确保视图没有父容器
            (view.parent as? ViewGroup)?.removeView(view)

            // 确保新视图的背景色正确
            val isDarkMode =
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val bgColor = if (isDarkMode) DARK_MODE_BG_COLOR else LIGHT_MODE_BG_COLOR
            view.setBackgroundColor(bgColor)

            // 添加视图到容器
            try {
                mainContainer?.addView(view)
            } catch (e: Exception) {
                // 如果添加失败，清理缓存并重新创建
                clearViewCache()
                val newView = when (newState) {
                    KeyboardState.VOICE -> createVoiceInputView()
                    KeyboardState.EMOJI -> createEmojiKeyboardView()
                    KeyboardState.SYMBOLS -> createSymbolKeyboardView()
                    KeyboardState.LATIN -> createLatinKeyboardView()
                }
                newView.setBackgroundColor(bgColor)
                mainContainer?.addView(newView)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearViewCache()
        mainContainer = null
    }

    /**
     * 创建拉丁字母键盘视图 - QWERTY布局
     */
    private fun createLatinKeyboardView(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // --- 数字行工具栏 ---
        val numberRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 10, 0, 10) }
            gravity = Gravity.CENTER_VERTICAL
        }

        // 数字按键
        val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        numbers.forEach { number ->
            val numberButton = TextView(this).apply {
                text = number
                textSize = 20f
                setTextColor(Color.rgb(150, 150, 150))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, 120, 1.0f) // 和主键盘一样的高度
                setBackgroundColor(Color.TRANSPARENT)
                // 添加点击效果
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground,
                    outValue,
                    true
                )
                setBackgroundResource(outValue.resourceId)
                setOnClickListener {
                    currentInputConnection?.commitText(number, 1)
                }
            }
            numberRow.addView(numberButton)
        }

        layout.addView(numberRow)

        // QWERTY键盘布局 - 只保留字母行
        val qwertyRows = listOf(
            // 第一行
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            // 第二行
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            // 第三行
            listOf("Z", "X", "C", "V", "B", "N", "M")
        )

        // 是否为大写模式
        var isUpperCase = true

        // 创建键盘行容器
        val keyboardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                500 // 固定高度
            )
            setPadding(8, 8, 8, 8)
        }

        // 创建各行按键 - 恢复原来的索引
        qwertyRows.forEachIndexed { rowIndex, row ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1.0f // 每行平分高度
                )
                gravity = Gravity.CENTER
            }

            // 第二行和第三行需要添加适当的缩进
            if (rowIndex == 1) {
                // 第二行左侧添加半个按键的空白
                val leftSpacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 0.5f)
                }
                rowLayout.addView(leftSpacer)
            } else if (rowIndex == 2) {
                // 第三行左侧添加Shift键
                val shiftButton = TextView(this).apply {
                    text = "⇧"
                    textSize = 20f
                    setTextColor(Color.rgb(150, 150, 150))
                    gravity = Gravity.CENTER
                    layoutParams =
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f)
                    setBackgroundColor(Color.TRANSPARENT)
                    // 添加点击效果
                    val outValue = android.util.TypedValue()
                    context.theme.resolveAttribute(
                        android.R.attr.selectableItemBackground,
                        outValue,
                        true
                    )
                    setBackgroundResource(outValue.resourceId)
                    setOnClickListener {
                        isUpperCase = !isUpperCase
                        updateKeyboardCase(keyboardContainer, isUpperCase)
                    }
                }
                rowLayout.addView(shiftButton)
            }

            // 添加字母按键
            row.forEach { letter ->
                val keyButton = TextView(this).apply {
                    text = if (isUpperCase) letter.uppercase() else letter.lowercase()
                    textSize = 20f
                    setTextColor(Color.rgb(150, 150, 150))
                    gravity = Gravity.CENTER
                    layoutParams =
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f)
                    setBackgroundColor(Color.TRANSPARENT)
                    // 添加点击效果
                    val outValue = android.util.TypedValue()
                    context.theme.resolveAttribute(
                        android.R.attr.selectableItemBackground,
                        outValue,
                        true
                    )
                    setBackgroundResource(outValue.resourceId)
                    setOnClickListener {
                        val textToInput =
                            if (isUpperCase) letter.uppercase() else letter.lowercase()
                        currentInputConnection?.commitText(textToInput, 1)
                    }
                }
                rowLayout.addView(keyButton)
            }

            // 第三行右侧添加退格键
            if (rowIndex == 2) {
                val backspaceButton = TextView(this).apply {
                    text = "⌫"
                    textSize = 20f
                    setTextColor(Color.rgb(150, 150, 150))
                    gravity = Gravity.CENTER
                    layoutParams =
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.5f)
                    setBackgroundColor(Color.TRANSPARENT)
                    // 添加点击效果
                    val outValue = android.util.TypedValue()
                    context.theme.resolveAttribute(
                        android.R.attr.selectableItemBackground,
                        outValue,
                        true
                    )
                    setBackgroundResource(outValue.resourceId)
                    setOnClickListener { performDelete() }
                    // 长按删除功能
                    val deleteHandler = Handler(Looper.getMainLooper())
                    var deleteRunnable: Runnable? = null
                    setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                deleteRunnable = object : Runnable {
                                    override fun run() {
                                        performDelete()
                                        deleteHandler.postDelayed(this, 100)
                                    }
                                }
                                deleteHandler.postDelayed(deleteRunnable!!, 500)
                                false
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                deleteRunnable?.let { deleteHandler.removeCallbacks(it) }
                                deleteRunnable = null
                                false
                            }
                            else -> false
                        }
                    }
                }
                rowLayout.addView(backspaceButton)
            }

            // 第二行右侧添加空白以保持对称
            if (rowIndex == 1) {
                val rightSpacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 0.5f)
                }
                rowLayout.addView(rightSpacer)
            }

            keyboardContainer.addView(rowLayout)
        }

        layout.addView(keyboardContainer)


        // --- 底部操作栏 ---
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 10)
        }

        // 返回主键盘的按钮
        val backToVoiceButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_keyboard)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(0, 120, 1.0f)
            setOnClickListener { switchKeyboardView(KeyboardState.VOICE) }
        }
        bottomBar.addView(backToVoiceButton)

        // 空格键
        val spaceButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_space_bar)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(0, 120, 3.0f)
            setOnClickListener { currentInputConnection?.commitText(" ", 1) }
        }
        bottomBar.addView(spaceButton)

        // 回车键
        val enterButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_send)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(0, 120, 1.0f)
            setOnClickListener {
                currentInputConnection?.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_ENTER
                    )
                )
                currentInputConnection?.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_ENTER
                    )
                )
            }
        }
        bottomBar.addView(enterButton)

        layout.addView(bottomBar)

        return layout
    }


    /**
     * 更新键盘大小写状态的辅助方法
     */
    private fun updateKeyboardCase(keyboardContainer: LinearLayout, isUpperCase: Boolean) {
        for (i in 0 until keyboardContainer.childCount) {
            val rowLayout = keyboardContainer.getChildAt(i) as LinearLayout
            for (j in 0 until rowLayout.childCount) {
                val child = rowLayout.getChildAt(j)
                if (child is TextView) {
                    val currentText = child.text.toString()
                    // 只更新字母按键，不更新数字、符号和特殊按键
                    if (currentText.matches(Regex("[A-Za-z]"))) {
                        child.text =
                            if (isUpperCase) currentText.uppercase() else currentText.lowercase()
                    }
                }
            }
        }
    }


    /**
     * 创建主语音输入界面
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun createVoiceInputView(): View {
        // 这里是几乎所有你原来的 onCreateInputView 代码
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val buttonHeight = 120

        // --- 顶部工具栏 ---
        val topButtonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 10, 0, 10) }
            gravity = Gravity.CENTER_VERTICAL
        }
        // ... (省略了顶部按钮的创建代码，和你的原代码完全一样)
        // 左边按钮：设置
        val settingsButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_settings) // 假设你有一个设置图标
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY) // 设置图标颜色为灰色
            layoutParams = LinearLayout.LayoutParams(0, buttonHeight).apply {
                weight = 1.0f // 让按钮平分空间
                gravity = Gravity.CENTER // 居中显示
            }
            setOnClickListener {
                // 打开你的主App设置界面
                // 假设你的主App的启动Activity是 MainActivity
                val intent = Intent(this@VoiceInputMethodService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // 确保在新的任务中启动
                }
                startActivity(intent)
            }
        }
        topButtonRow.addView(settingsButton)

        // 中间按钮：切换输入法 (地球图标)
        val switchInputMethodButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_language) // 假设你有一个地球图标 (language)
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY) // 设置图标颜色为灰色
            layoutParams = LinearLayout.LayoutParams(0, buttonHeight).apply {
                weight = 1.0f // 让按钮平分空间
                gravity = Gravity.CENTER // 居中显示
            }
            setOnClickListener {
                // 弹出切换输入法的对话框
                val imm =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
            }
        }
        topButtonRow.addView(switchInputMethodButton)


        // 全选按钮（带备用方案）
        val selectAllButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_select_all) // 假设你有一个全选图标
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY) // 设置图标颜色为灰色
            layoutParams = LinearLayout.LayoutParams(0, buttonHeight).apply {
                weight = 1.0f // 根据需要调整权重
                gravity = Gravity.CENTER // 居中显示
            }
            setOnClickListener {
                // 执行全选操作
                val inputConnection = currentInputConnection
                if (inputConnection != null) {
                    try {
                        // 方法1：使用 Ctrl+A 组合键
                        val selectAllSuccess = inputConnection.performContextMenuAction(
                            android.R.id.selectAll
                        )

                        if (!selectAllSuccess) {
                            // 方法2：如果方法1失败，尝试使用 performContextMenuAction
                            inputConnection.performContextMenuAction(android.R.id.selectAll)
                        }

                        Toast.makeText(this@VoiceInputMethodService, "已全选", Toast.LENGTH_SHORT)
                            .show()
                    } catch (e: Exception) {
                        // 如果所有方法都失败，提示用户手动选择
                        Toast.makeText(
                            this@VoiceInputMethodService,
                            "全选失败，请手动选择文本",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        topButtonRow.addView(selectAllButton)

// 复制按钮
        val copyButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_copy) // 假设你有一个复制图标
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY) // 设置图标颜色为灰色
            layoutParams = LinearLayout.LayoutParams(0, buttonHeight).apply {
                weight = 1.0f // 根据需要调整权重
                gravity = Gravity.CENTER // 居中显示
            }
            setOnClickListener {
                // 获取当前选中的文本
                val inputConnection = currentInputConnection
                if (inputConnection != null) {
                    val selectedText = inputConnection.getSelectedText(0)
                    if (selectedText != null && selectedText.isNotEmpty()) {
                        // 复制选中的文本到剪贴板
                        val clipboard =
                            getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip =
                            android.content.ClipData.newPlainText("Copied Text", selectedText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@VoiceInputMethodService, "已复制", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        Toast.makeText(
                            this@VoiceInputMethodService,
                            "请先选择要复制的文本",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        topButtonRow.addView(copyButton)
// 剪切按钮
        val cutButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_cut) // 假设你有一个剪切图标
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY) // 设置图标颜色为灰色
            layoutParams = LinearLayout.LayoutParams(0, buttonHeight).apply {
                weight = 1.0f // 根据需要调整权重
                gravity = Gravity.CENTER // 居中显示
            }
            setOnClickListener {
                // 获取当前选中的文本
                val inputConnection = currentInputConnection
                if (inputConnection != null) {
                    val selectedText = inputConnection.getSelectedText(0)
                    if (selectedText != null && selectedText.isNotEmpty()) {
                        // 复制选中的文本到剪贴板
                        val clipboard =
                            getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Cut Text", selectedText)
                        clipboard.setPrimaryClip(clip)

                        // 删除选中的文本
                        inputConnection.commitText("", 1)
                        Toast.makeText(this@VoiceInputMethodService, "已剪切", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        Toast.makeText(
                            this@VoiceInputMethodService,
                            "请先选择要剪切的文本",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        topButtonRow.addView(cutButton)

        // 第3个按钮：粘贴键
        val pasteButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_paste) // 假设你有一个粘贴图标
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY) // 设置图标颜色为灰色
            layoutParams = LinearLayout.LayoutParams(0, buttonHeight).apply {
                weight = 1.0f // 平分空间
                gravity = Gravity.CENTER // 居中显示
            }
            setOnClickListener {
                // 获取剪贴板内容并粘贴
                val clipboard =
                    getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val pasteText = clip.getItemAt(0).text
                    if (pasteText != null) {
                        currentInputConnection?.commitText(pasteText, 1)
                    } else {
                        Toast.makeText(
                            this@VoiceInputMethodService,
                            "剪贴板为空",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(this@VoiceInputMethodService, "剪贴板为空", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
        topButtonRow.addView(pasteButton)
        // 右边按钮：收起输入法 (下箭头图标)
        val hideInputMethodButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_keyboard_arrow_down) // 假设你有一个向下箭头的图标
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY) // 设置图标颜色为灰色
            layoutParams = LinearLayout.LayoutParams(0, buttonHeight).apply {
                weight = 1.0f // 让按钮平分空间
                gravity = Gravity.CENTER // 居中显示
            }
            setOnClickListener {
                // 请求隐藏输入法键盘
                requestHideSelf(0)
            }
        }
        topButtonRow.addView(hideInputMethodButton)

        layout.addView(topButtonRow)

        // --- 中间录音按钮行 ---
        val middleButtonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 10, 0, 10) }
            gravity = Gravity.CENTER_VERTICAL
        }

        // *** 修改：左边按钮，用于切换到 Emoji 键盘 ***
        val emojiButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_emoji) // 使用新的 Emoji 图标
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY)
            layoutParams = LinearLayout.LayoutParams(0, buttonHeight).apply {
                weight = 1.0f
                gravity = Gravity.CENTER
            }
            setOnClickListener {
                switchKeyboardView(KeyboardState.EMOJI) // 点击切换到 Emoji 视图
            }
        }
        middleButtonRow.addView(emojiButton)

        // 语音按钮 (你的原代码)
        val currentOrientation = resources.configuration.orientation
        val voiceButtonHeight =
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) 200 else 450
        voiceButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_mic_normal)
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY)
            layoutParams = LinearLayout.LayoutParams(0, voiceButtonHeight).apply {
                weight = 2.0f
                gravity = Gravity.CENTER
            }
            setOnTouchListener { _, event ->
                // ... (省略了 setOnTouchListener 的内部代码，和你的原代码完全一样)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = event.rawX
                        initialY = event.rawY
                        isMovingCursor = false
                        isRecording = false

                        // 设置延迟，如果在指定时间内没有滑动就开始录音
                        mainHandler.postDelayed({
                            if (!isMovingCursor) {
                                startRecording()
                                isRecording = true
                            }
                        }, recordingDelayTime)
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialX
                        val deltaY = event.rawY - initialY

                        // 检查是否满足移动条件且还没开始录音
                        if (!isRecording && shouldStartCursorMovement(deltaX, deltaY)) {
                            if (!isMovingCursor) {
                                isMovingCursor = true
                                // 取消录音延迟任务
                                mainHandler.removeCallbacksAndMessages(null)

                                // 更新按钮样式，表示进入光标移动模式
                                voiceButton.setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN)
                            }

                            // 移动光标
                            moveCursor(deltaX, deltaY)

                            // 更新初始位置，实现连续移动
                            initialX = event.rawX
                            initialY = event.rawY
                        }
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // 取消所有延迟任务
                        mainHandler.removeCallbacksAndMessages(null)

                        when {
                            isRecording -> {
                                // 如果正在录音，停止录音并识别
                                isRecording = false
                                stopRecordingAndRecognize()
                            }

                            isMovingCursor -> {
                                // 如果是光标移动模式，恢复按钮样式
                                isMovingCursor = false
                                voiceButton.clearColorFilter()
                            }

                            else -> {
                                // 如果既没有录音也没有移动光标（快速点击），什么都不做
                                // 或者可以在这里添加快速点击的处理逻辑
                            }
                        }
                        true
                    }

                    else -> false
                }
            }
        }
        middleButtonRow.addView(voiceButton)

        // 退格键 (你的原代码)
        val backspaceButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_backspace)
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY)
            layoutParams = LinearLayout.LayoutParams(0, buttonHeight).apply {
                weight = 1.0f
                gravity = Gravity.CENTER
            }
            val deleteHandler = Handler(Looper.getMainLooper())
            var deleteRunnable: Runnable? = null
            setOnClickListener { performDelete() }
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        deleteRunnable = object : Runnable {
                            override fun run() {
                                performDelete()
                                deleteHandler.postDelayed(this, 100)
                            }
                        }
                        deleteHandler.postDelayed(deleteRunnable!!, 200)
                        false
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        deleteRunnable?.let { deleteHandler.removeCallbacks(it) }
                        deleteRunnable = null
                        false
                    }

                    else -> false
                }
            }
        }
        middleButtonRow.addView(backspaceButton)
        layout.addView(middleButtonRow)

        // --- 底部工具栏 ---
        val bottomButtonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 10, 0, 20) }
            gravity = Gravity.CENTER_VERTICAL
        }

        val latinButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_abc) // 需要添加拉丁字母的图标
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY)
            layoutParams = LinearLayout.LayoutParams(0, buttonHeight).apply {
                weight = 0.6f
                gravity = Gravity.CENTER
            }
            setOnClickListener {
                switchKeyboardView(KeyboardState.LATIN) // 点击切换到拉丁字母视图
            }
        }
        bottomButtonRow.addView(latinButton)


        // *** 修改：左下角按钮，用于切换到符号键盘 ***
        val symbolButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_123) // 使用新的符号图标
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY)
            layoutParams = LinearLayout.LayoutParams(0, buttonHeight).apply {
                weight = 0.6f
                gravity = Gravity.CENTER
            }
            setOnClickListener {
                switchKeyboardView(KeyboardState.SYMBOLS) // 点击切换到符号视图
            }
        }
        bottomButtonRow.addView(symbolButton)

        // 空格键 (你的原代码)
        val spaceButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_space_bar)
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY)
            layoutParams = LinearLayout.LayoutParams(0, buttonHeight).apply {
                weight = 2.0f
                gravity = Gravity.CENTER
            }
            setOnClickListener { currentInputConnection?.commitText(" ", 1) }
        }
        bottomButtonRow.addView(spaceButton)

        // 回车键 (你的原代码)
        val enterButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_send)
            setBackgroundColor(Color.TRANSPARENT)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY)
            layoutParams = LinearLayout.LayoutParams(0, buttonHeight).apply {
                weight = 1.0f
                gravity = Gravity.CENTER
            }
            setOnClickListener {
                currentInputConnection?.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_ENTER
                    )
                )
                currentInputConnection?.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_ENTER
                    )
                )
            }
        }
        bottomButtonRow.addView(enterButton)
        layout.addView(bottomButtonRow)

        return layout
    }

    /**
     * 创建 Emoji 键盘视图
     */
    private fun createEmojiKeyboardView(): View {
        val emojiList = listOf(
            // 笑脸表情
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
            "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
            "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
            "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
            "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
            "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗",
            "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯",
            "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐",
            "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈",
            "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾",

            // 手势
            "👍", "👎", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙",
            "👈", "👉", "👆", "🖕", "👇", "☝️", "👋", "🤚", "🖐", "✋",
            "🖖", "👏", "🙌", "🤲", "🤝", "🙏", "✍️", "💪", "🦾", "🦿",

            // 心形和符号
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
            "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "💌",
            "💯", "💢", "💥", "💫", "💦", "💨", "🕳️", "💤", "🔥", "⭐",
            "🌟", "✨", "⚡", "☄️", "💍", "🎁", "🎉", "🎊", "🎈", "🎀",

            // 其他常用
            "☀️", "🌙", "⭐", "🌈", "☁️", "⛅", "🌤️", "🌦️", "🌧️", "⛈️",
            "🌩️", "🌨️", "❄️", "☃️", "⛄", "🌬️", "💨", "🌪️", "🌊", "💧"
        )
        return createGridKeyboardView(emojiList, 8) { // 8 列
            switchKeyboardView(KeyboardState.VOICE)
        }
    }

    /**
     * 创建符号和数字键盘视图
     */
    private fun createSymbolKeyboardView(): View {
        val symbolList = listOf(
            // 数字
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",

            // 常用符号
            "@", "#", "$", "%", "&", "*", "-", "+", "=", "_",
            "(", ")", "[", "]", "{", "}", "<", ">", "!", "?",
            ".", ",", ":", ";", "'", "\"", "/", "\\", "|", "~",

            // 更多符号
            "^", "`", "€", "£", "¥", "¢", "°", "§", "¶", "•",
            "‰", "†", "‡", "…", "‹", "›", "«", "»", "‚", "„",
            """, """, "'", "'", "¡", "¿", "¦", "¨", "¯", "´",
            "¸", "¹", "²", "³", "¼", "½", "¾", "±", "×", "÷",
            "α", "β", "γ", "δ", "π", "Ω", "μ", "Σ", "∞", "∑",
            "√", "∫", "∂", "∆", "∇", "∈", "∉", "∋", "∌", "∅",
            "∩", "∪", "⊂", "⊃", "⊆", "⊇", "⊕", "⊗", "⊥", "∥",
            "→", "←", "↑", "↓", "↔", "↕", "↖", "↗", "↘", "↙",
            "⇒", "⇐", "⇑", "⇓", "⇔", "⇕", "⌈", "⌉", "⌊", "⌋"
        )
        return createGridKeyboardView(symbolList, 10) { // 10 列
            switchKeyboardView(KeyboardState.VOICE)
        }
    }

    /**
     * 通用的网格键盘创建工具
     */
    /**
     * 通用的网格键盘创建工具
     */
    private fun createGridKeyboardView(
        items: List<String>,
        spanCount: Int,
        onBack: () -> Unit
    ): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 创建 RecyclerView 用于显示网格，设置固定高度以匹配主键盘
        val recyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                500 // 固定高度，与主键盘高度一致
            )
            layoutManager = GridLayoutManager(this@VoiceInputMethodService, spanCount)
            adapter = GridKeyAdapter(items) { key ->
                currentInputConnection?.commitText(key, 1)
            }
        }
        layout.addView(recyclerView)

        // --- 底部操作栏 ---
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 10)
        }

        // 返回主键盘的按钮
        val backToVoiceButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_keyboard)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(0, 120, 1.0f)
            setOnClickListener { onBack() }
        }
        bottomBar.addView(backToVoiceButton)

        // 空格键
        val spaceButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_space_bar)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(0, 120, 2.0f)
            setOnClickListener { currentInputConnection?.commitText(" ", 1) }
        }
        bottomBar.addView(spaceButton)

        // 退格键
        val backspaceButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_backspace)
            imageTintList = ColorStateList.valueOf(ICON_COLOR_GRAY)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(0, 120, 1.0f)
            setOnClickListener { performDelete() }
            // 长按删除功能
            val deleteHandler = Handler(Looper.getMainLooper())
            var deleteRunnable: Runnable? = null
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        deleteRunnable = object : Runnable {
                            override fun run() {
                                performDelete()
                                deleteHandler.postDelayed(this, 100)
                            }
                        }
                        deleteHandler.postDelayed(deleteRunnable!!, 500)
                        false
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        deleteRunnable?.let { deleteHandler.removeCallbacks(it) }
                        deleteRunnable = null
                        false
                    }

                    else -> false
                }
            }
        }
        bottomBar.addView(backspaceButton)

        layout.addView(bottomBar)

        return layout
    }

    // --- 你的其他辅助方法，无需修改 ---
    private fun shouldStartCursorMovement(
        deltaX: Float,
        deltaY: Float
    ): Boolean { /* ... */ return kotlin.math.abs(deltaX) > horizontalMovementThreshold || kotlin.math.abs(
        deltaY
    ) > verticalMovementThreshold
    }

    private fun performDelete() { /* ... */
        val inputConnection = currentInputConnection ?: return;
        val selectedText =
            inputConnection.getSelectedText(0); if (selectedText != null && selectedText.isNotEmpty()) {
            inputConnection.commitText("", 1)
        } else {
            if (!inputConnection.deleteSurroundingText(1, 0)) {
                inputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DEL
                    )
                ); inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
        }
    }

    private fun moveCursor(deltaX: Float, deltaY: Float) { /* ... */
        val inputConnection = currentInputConnection ?: return;
        val absX = kotlin.math.abs(deltaX);
        val absY = kotlin.math.abs(deltaY);
        val horizontalMoveDistance = (absX / 30f).toInt().coerceAtLeast(1);
        val verticalMoveDistance = (absY / 80f).toInt().coerceAtLeast(1); when {
            absX > absY -> if (deltaX > 0) repeat(horizontalMoveDistance) {
                inputConnection.sendKeyEvent(
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
                ); inputConnection.sendKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_DPAD_RIGHT
                )
            )
            } else repeat(horizontalMoveDistance) {
                inputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DPAD_LEFT
                    )
                ); inputConnection.sendKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_DPAD_LEFT
                )
            )
            }; absY > absX -> if (deltaY < 0) repeat(verticalMoveDistance) {
                inputConnection.sendKeyEvent(
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
                ); inputConnection.sendKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_DPAD_UP
                )
            )
            } else repeat(verticalMoveDistance) {
                inputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DPAD_DOWN
                    )
                ); inputConnection.sendKeyEvent(
                KeyEvent(
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN
                )
            )
            }
        }
    }

    private fun startRecording() { /* ... */ if (asrUrl.isBlank()) {
        Toast.makeText(this, "请先打开App主程序设置ASR服务器地址", Toast.LENGTH_LONG).show(); return
    }; if (!isMovingCursor) {
        voiceButton.setImageResource(R.drawable.ic_mic_recording); voiceButton.setColorFilter(
            Color.RED,
            PorterDuff.Mode.SRC_IN
        ); audioRecorder.startRecording()
    }
    }


    private fun stopRecordingAndRecognize() {
        // 还原按钮状态
        voiceButton.setImageResource(R.drawable.ic_mic_normal)
        voiceButton.clearColorFilter()

        // 停止录音
        val audioWavData = audioRecorder.stopRecording()

        // 音频存在且不为空时，进行识别
        if (audioWavData != null && audioWavData.isNotEmpty()) {
            asrClient.recognize(asrUrl, audioWavData) { resultText ->
                mainHandler.post {
                    val processedText = processPunctuation(resultText)
                    currentInputConnection?.commitText(processedText, 1)
                }
            }
        }
    }

    private fun processPunctuation(text: String): String {
        // 匹配中文和英文标点符号
        val punctuationRegex = Regex("""[,.!?;:，。！？；：、]""")
        val matches = punctuationRegex.findAll(text).toList()

        return if (matches.size == 1) {
            // 只有一个标点符号，删除它
            text.replace(punctuationRegex, "")
        } else {
            // 没有标点符号或多个标点符号，不做处理
            text
        }
    }

}

/**
 * RecyclerView 的适配器，用于显示网格中的按键
 */
class GridKeyAdapter(
    private val keys: List<String>,
    private val onKeyClick: (String) -> Unit
) : RecyclerView.Adapter<GridKeyAdapter.KeyViewHolder>() {

    class KeyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val keyButton: TextView = view as TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeyViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                140 // 每个格子的高度
            )
            gravity = Gravity.CENTER
            textSize = 24f // 字体大小
            setTextColor(Color.rgb(150, 150, 150)) // 使用与主键盘一致的灰色
            // 设置点击波纹效果
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }
        return KeyViewHolder(textView)
    }

    override fun onBindViewHolder(holder: KeyViewHolder, position: Int) {
        val key = keys[position]
        holder.keyButton.text = key
        holder.keyButton.setOnClickListener {
            onKeyClick(key)
        }
    }

    override fun getItemCount() = keys.size
}

