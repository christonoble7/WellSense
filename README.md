# WellSense

**Explainable Multimodal AI for Personalized Wellness Monitoring** (B.Tech seminar CDQ 413)

> **Not a medical device.** WellSense gives indicative wellness information only. It does not diagnose, treat or prevent any disease. Consult a doctor for medical decisions.

An Android app (web UI wrapped with Capacitor, plus native plugins) that combines nutrition, wearable data, blood-report values, phone-usage signals and mood into a cardiac-focused Wellness Score (0-100), computed on-device and explained factor by factor ("Why this score?"), compared against the user's own baseline.

## Features
- Profile with optional PIN, personal baseline from the first readings
- Nutrition: built-in Indian and general food database, unit conversion, meal diary, optional USDA lookup (own API key)
- Body: manual entry or sync from **Health Connect** (steps, heart rate, resting HR, HRV, sleep, SpO2, exercise)
- Blood report with normal / borderline / high indicators and trends
- Mind: mood check-in and **real phone-usage tracking** (screen time, app opens per hour, night use) via Android Usage Access
- Heart-risk part of the score uses a **trained logistic-regression model**; its per-feature contributions are exact SHAP values for a linear model
- PDF report, JSON and CSV export; fully offline

## Important notes
- **The model in this repository is trained on synthetic (fake) data** only to prove the pipeline works. Retrain it on a real dataset with `training/train.py` (see below) before relying on or presenting its metrics.
- The model uses five clinical features (age, sex, blood pressure, total cholesterol, glucose). It is a population-level estimate, not a diagnosis.
- Training happens on a computer, not on the phone. The app runs the finished model.
- Wearable sync only works if the watch's own app shares data to Health Connect (Huawei Health does not; a bridge app is needed).
- Screen time is approximate; typing speed is entered manually.

## Quick start
Full step-by-step instructions are in [SETUP.txt](SETUP.txt). In short:

```
# 1. (recommended) train on a real dataset
cd training
pip install pandas scikit-learn
python train.py heart.csv          # then copy model-data.js into ../www/

# 2. build the Android app (needs Node.js LTS + Android Studio)
cd ..
npm install
node setup.js
npx cap open android               # press Run with your phone connected
```

Supported training files: Kaggle `heart.csv`, Kaggle "Heart Failure Prediction" `heart.csv`, UCI `processed.cleveland.data`. Datasets are not included; obtain them from their original sources and respect their licenses.

## Project layout
- `www/` app UI, scoring engine, PDF writer, model data
- `native/` Android plugins (usage tracking in Java, Health Connect in Kotlin)
- `training/` model training script
- `setup.js` automates the Android project configuration

## Status
The web logic, PDF export and training script were tested in a simulated environment. The Android plugins have not been compiled or run on a device by the author of this template; expect to fix small build errors on first build.

## License
MIT (see LICENSE). Replace `<YOUR NAME>` in the license file.
