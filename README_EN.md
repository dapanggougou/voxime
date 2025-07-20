<p align="center">
  <img width="170" height="170" alt="VOXIME Icon" src="https://github.com/user-attachments/assets/8ea0f822-96eb-4914-9022-147d7b6af10c" />
</p>

<h1 align="center">VOXIME</h1>
<h3 align="center">SenseVox Input Method</h3>

<p align="center">An Android input method based on speech recognition, communicating with local or remote ASR services via HTTP.</p>

<p align="center">
  <img src="./image.jpg" alt="Project Illustration" width="300" />
</p>

---

## 🚀 How to Use [▶ Watch Demo](./demo.mp4)

### 1. Set the Server Address

Enter the address of your ASR service on the app's main screen:

- Example for local server:
  ```
  http://127.0.0.1:8000/asr
  ```

### 2. Start Voice Input

- **Long press the record button** to start recording.
- **Release the button** to automatically send the audio data to the server for recognition.

---

## 📡 API Specification

Server programs: [asrmaid](https://github.com/dapanggougou/asrmaid) , [asrmaid_python](https://github.com/dapanggougou/asrmaid_python)

Currently supports only the following interface format:

```bash
curl -X POST http://127.0.0.1:8000/asr \
     --header "Content-Type: audio/wav" \
     --data-binary "@zh.wav"
```

### ✅ Response Format:

```json
{
  "status": "success",
  "result": "Opening hours are from 9 AM to 5 PM."
}
```

---

## ✨ Features

- While holding the record button, sliding your finger moves the cursor up/down/left/right instantly.
  *(Note: The cursor may move outside the input field into other components — whether this is a bug is still under consideration.)*

---

## 📝 To-Do List

- [ ] AI-based typo correction
- [ ] Customizable input method background
- [ ] Remove punctuation from recognition results / replace punctuation with spaces

---

## 🔧 Advanced Tips (Root Required)

If you have **root access**, you can quickly switch input methods using the following method:

1. View help:
   ```bash
   adb shell ime
   ```
2. Get the list of installed input methods:
   ```bash
   adb shell ime list
   ```
3. Copy the output and ask an AI to generate the switching command. For example, to switch to this input method:
   ```bash
   adb shell ime set sensevox.asr.voiceinputmethod/.VoiceInputMethodService
   ```
4. Use other tools/scripts to execute the command for quick switching.

---

## 👥 Code Contribution Credits

| Contributor Model     | Estimated Contribution |
|-----------------------|------------------------|
| Claude 4              | ⭐⭐⭐⭐⭐                 |
| Gemini 2.5 Pro        | ⭐⭐⭐⭐                  |
| Gemini 2.5 Flash      | ⭐⭐⭐                   |

---

## 📄 License

To be determined.
