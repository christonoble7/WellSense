"""
WellSense - train the clinical (blood-report) part of the score.

USAGE (real data):   python train.py heart.csv
USAGE (pipeline test only, FAKE data):   python train.py --synthetic

heart.csv = UCI/Kaggle "Heart Disease" file with columns:
  age, sex, trestbps (resting systolic BP), chol (total cholesterol), fbs (fasting glucose >120 mg/dL), target (or num)
Output: model.json  (coefficients + scaling + honest metrics), loaded by the app and run on-device.
"""
import json, sys
import numpy as np, pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.metrics import roc_auc_score, accuracy_score
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

FEATS = ["age", "sex", "trestbps", "chol", "fbs"]

def synthetic(n=1000, seed=0):
    """FAKE data, only to prove the pipeline runs. Never report its metrics as real."""
    r = np.random.default_rng(seed)
    d = pd.DataFrame({"age": r.integers(30, 78, n), "sex": r.integers(0, 2, n),
                      "trestbps": r.normal(130, 17, n).round(), "chol": r.normal(245, 50, n).round(),
                      "fbs": (r.random(n) < .15).astype(int)})
    z = .05*(d.age-54) + .5*d.sex + .02*(d.trestbps-130) + .008*(d.chol-245) + .4*d.fbs
    d["target"] = (r.random(n) < 1/(1+np.exp(-z))).astype(int)
    return d

def load(path):
    """Accepts: UCI processed.cleveland.data (no header, '?' = missing), the standard Kaggle heart.csv
    (age,sex,cp,trestbps,chol,fbs,...,target) and the Kaggle 'Heart Failure Prediction' file
    (Age,Sex=M/F,RestingBP,Cholesterol,FastingBS,...,HeartDisease)."""
    raw = pd.read_csv(path, header=None, na_values="?", nrows=2)
    headerless = pd.to_numeric(raw.iloc[0], errors="coerce").notna().sum() >= raw.shape[1] // 2   # numbers in row 1 = no header
    if headerless:
        d = pd.read_csv(path, header=None, na_values="?")
        d.columns = ["age","sex","cp","trestbps","chol","fbs","restecg","thalach","exang","oldpeak","slope","ca","thal","target"][:d.shape[1]]
    else:
        d = pd.read_csv(path, na_values="?")
    d.columns = [str(c).strip().lower() for c in d.columns]
    d = d.rename(columns={"restingbp":"trestbps","resting_bp":"trestbps","cholesterol":"chol","fastingbs":"fbs",
                          "heartdisease":"target","output":"target","num":"target","heart_disease":"target"})
    missing = [c for c in FEATS + ["target"] if c not in d.columns]
    if missing:
        sys.exit(f"Could not find columns {missing}. Found: {list(d.columns)}. Rename them to match, or send the header line to the developer.")
    if not pd.api.types.is_numeric_dtype(d["sex"]):                      # M/F text -> 1/0 (male=1, same as the app)
        d["sex"] = d["sex"].astype(str).str.strip().str.upper().map({"M":1,"MALE":1,"F":0,"FEMALE":0})
    d["target"] = (pd.to_numeric(d["target"], errors="coerce") > 0).astype(int)   # 0 = healthy, >0 = disease
    d = d[FEATS + ["target"]].apply(pd.to_numeric, errors="coerce")
    n0 = len(d)
    d = d[(d["chol"] > 0) & (d["trestbps"] > 0)].dropna()   # Kaggle file stores missing chol/BP as 0
    print(f"Loaded {n0} rows, using {len(d)} after removing missing/zero values.")
    return d

if len(sys.argv) < 2:
    sys.exit(__doc__)
fake = sys.argv[1] == "--synthetic"
df = synthetic() if fake else load(sys.argv[1])
if df["target"].nunique() < 2 or len(df) < 100:
    sys.exit("Not enough usable rows (need 100+ with both outcomes).")
X, y = df[FEATS].values.astype(float), df["target"].values
Xtr, Xte, ytr, yte = train_test_split(X, y, test_size=.25, stratify=y, random_state=42)

pipe = make_pipeline(StandardScaler(), LogisticRegression(max_iter=1000))
cv = cross_val_score(pipe, Xtr, ytr, cv=5, scoring="roc_auc")
pipe.fit(Xtr, ytr)
p = pipe.predict_proba(Xte)[:, 1]
m = {"rows": int(len(df)), "cv_auc_mean": round(float(cv.mean()), 3), "cv_auc_std": round(float(cv.std()), 3),
     "test_auc": round(float(roc_auc_score(yte, p)), 3), "test_accuracy": round(float(accuracy_score(yte, p > .5)), 3),
     "synthetic_data": fake}
sc, lr = pipe[0], pipe[1]
out = {"features": FEATS, "mean": sc.mean_.tolist(), "std": sc.scale_.tolist(),
       "coef": lr.coef_[0].tolist(), "intercept": float(lr.intercept_[0]), "metrics": m,
       "note": "Population-level risk estimate from 5 clinical features. Not a diagnosis."}
json.dump(out, open("model.json", "w"), indent=1)
open("model-data.js", "w").write("window.WS_MODEL=" + json.dumps(out) + ";\n")  # copy this file into ../www/
print(json.dumps(m, indent=1))
if fake: print("\nWARNING: synthetic data. Metrics are meaningless; run on the real heart.csv.")
