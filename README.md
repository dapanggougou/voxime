<p align="center">
  <img width="170" height="170" alt="VOXIME Icon" src="https://github.com/user-attachments/assets/820c736c-5fc4-44af-9e10-5568ab8a5818" />
</p>

<h1 align="center"> VOXIME</h1>
<h3 align="center"> SenseVox 输入法</h3>

<p align="center">一个基于语音识别的 Android 输入法，通过 HTTP 接口与本地或远程 ASR 服务通信。</p>

<p align="center">
  <img src="./image.jpg" alt="项目示意图" width="300" />
</p>

---

## 🚀 使用方法 [▶ 演示](./demo.mp4)


### 1. 设置服务器地址

在 App 主界面填写你的 ASR 服务地址：

- 本地服务器示例：
  ```
  http://127.0.0.1:8000/asr
  ```


### 2. 开始语音输入

- **长按录音按钮**开始录音。
- **松开按钮**后自动发送音频数据到服务器进行识别。

---

## 📡 接口说明

服务端程序：[asrmaid](https://github.com/dapanggougou/asrmaid) , [asrmaid_python](https://github.com/dapanggougou/asrmaid_python)

当前仅支持以下接口格式：

```bash
curl -X POST http://127.0.0.1:8000/asr \
     --header "Content-Type: audio/wav" \
     --data-binary "@zh.wav"
```

### ✅ 响应格式：

```json
{
  "status": "success",
  "result": "开放时间早上9点至下午5点。"
}
```

---

## ✨ 特性

- 按下录音键立刻滑动手指能上下左右移动光标（光标可能移出输入框到其他组件 算不算bug待定）

---

## 📝 待办事项

- [ ] AI 纠正错别字
- [ ] 自定义输入法背景图
- [ ] 移除识别结果里的标点符号/用空格替换标点符号

---

## 🔧 高级技巧（需 Root）

如你拥有 **root 权限**，可以使用以下方法快速切换输入法：

1. 查看帮助：
   ```bash
   adb shell ime
   ```
2. 获取当前输入法列表：
   ```bash
   adb shell ime list
   ```
3. 将以上输出复制给 AI，生成切换命令。例如，切换到本输入法的命令：
   ```bash
   adb shell ime set sensevox.asr.voiceinputmethod/.VoiceInputMethodService
   ```
4. 使用其他工具等执行命令快速切换。
	
---

## 👥 代码贡献度

| 贡献者模型 | 比例估计 |
|------------|----------|
| Claude 4   | ⭐⭐⭐⭐⭐ |
| Gemini 2.5 Pro | ⭐⭐⭐⭐ |
| Gemini 2.5 Flash | ⭐⭐⭐ |

---

## 📄 License

待定
