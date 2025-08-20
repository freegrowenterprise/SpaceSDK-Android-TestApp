# 📡 Space UWB SDK Example App (Android)

This project is the **official SDK example app** provided by **FREEGROW Inc.**, designed to integrate Grow Space UWB products with Android devices.  
It enables direct testing of core UWB features such as BLE-based device discovery, distance and direction measurement, and RTLS-based real-time location tracking, all supported with **an intuitive UI and real-time visualization**.

---

## 🔧 Requirements

### Software
- Android 14 (API 34) or later
- Kotlin 1.9.22 or later

### Hardware
- [UWB-supported Android device](https://blog.naver.com/growdevelopers/223812647964)
- Physical UWB device [(Grow Space UWB product)](https://grow-space.io/product/n1-mk-01/)

---

## 🚀 Getting Started

1. Clone the repository:
```bash
git clone https://github.com/freegrowenterprise/SpaceSDK-Android-TestApp.git
```

2.	Open the project in Android Studio (open the directory containing build.gradle)


3. Connect a physical Android device via USB and run the app  
   > UWB features do not work on emulators.

---

## 📦 Included Features

### ✅ SDK Integration Example
- Implements the complete flow using the FREEGROW UWB SDK (SpaceUwb)
- covers devices via BLE and performs UWB-based ranging
- putes real-time location using an RTLS algorithm

### ✅ Key Features

#### 📏 Distance & Direction Measurement
- After connecting to the UWB device via BLE, the app measures and displays real-time distance, azimuth, and elevation values in the UI.
- Lists connected devices with their real-time data in the UI

https://github.com/user-attachments/assets/0a675881-3c08-4009-978a-acaaafa0b76d



#### 🧭 RTLS Grid-Based Location Visualization
- After assigning coordinate values to each UWB anchor device, the app uses an RTLS algorithm to calculate the current user position (x, y, z) in real time.
- Displays the estimated position in a grid-based UI (Canvas/Grid), visualizing user movement in indoor environments

https://github.com/user-attachments/assets/5191715b-4a3f-4a6c-b5aa-01c610312158

---

## 🏢 Developed by

**FREEGROW Inc.**
We specialize in indoor positioning and ultra-wideband (UWB) communication technologies for smart spatial solutions.

---

## 📫 Contact

For technical inquiries or suggestions, feel free to reach out to us:

📮 contact@freegrow.io

🌐 https://grow-space.io
