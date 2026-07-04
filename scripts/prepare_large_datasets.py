from __future__ import annotations

import json
import shutil
import sys
import urllib.request
import zipfile
import csv
import os
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
RAW_DIR = ROOT / "data" / "raw"
PROCESSED_DIR = ROOT / "data" / "processed"

BRFSS_XPT_ZIP_URL = "https://www.cdc.gov/brfss/annual_data/2024/files/LLCP2024XPT.zip"
UCI_HEART_ZIP_URL = "https://archive.ics.uci.edu/static/public/45/heart+disease.zip"
UCI_CKD_ZIP_URL = "https://archive.ics.uci.edu/static/public/336/chronic+kidney+disease.zip"
UCI_HEART_PAGE_URL = "https://archive.ics.uci.edu/dataset/45/heart+disease"
UCI_CKD_PAGE_URL = "https://archive.ics.uci.edu/dataset/336/chronic+kidney+disease"
UCI_CKD_ARFF_FALLBACK_URL = "https://raw.githubusercontent.com/yli110-stat697/Chronic-Kidney-Disease/master/Data/chronic_kidney_disease.arff"
MIMIC_IV_PAGE_URL = "https://physionet.org/content/mimiciv/3.1/"
NHANES_URLS = {
    "P_MCQ.XPT": "https://wwwn.cdc.gov/Nchs/Data/Nhanes/Public/2017/DataFiles/P_MCQ.XPT",
    "P_BIOPRO.XPT": "https://wwwn.cdc.gov/Nchs/Data/Nhanes/Public/2017/DataFiles/P_BIOPRO.XPT",
    "P_DEMO.XPT": "https://wwwn.cdc.gov/Nchs/Data/Nhanes/Public/2017/DataFiles/P_DEMO.XPT",
    "P_BMX.XPT": "https://wwwn.cdc.gov/Nchs/Data/Nhanes/Public/2017/DataFiles/P_BMX.XPT",
}

BRFSS_KEEP_COLUMNS = [
    "GENHLTH",
    "PHYSHLTH",
    "MENTHLTH",
    "CHECKUP1",
    "EXERANY2",
    "CVDINFR4",
    "CVDCRHD4",
    "CVDSTRK3",
    "CHCKDNY2",
    "DIABETE4",
    "BPHIGH6",
    "TOLDHI3",
    "CHOLCHK3",
    "SMOKE100",
    "SMOKDAY2",
    "ALCDAY4",
    "DRNKANY6",
    "AVEDRNK4",
    "DRNK3GE5",
    "MAXDRNKS",
    "PERSDOC3",
    "MEDCOST1",
    "PRIMINS2",
    "EDUCA",
    "INCOME3",
    "DEAF",
    "BLIND",
    "DECIDE",
    "DIFFWALK",
    "DIFFDRES",
    "DIFFALON",
    "SEXVAR",
    "_SEX",
    "_AGE80",
    "_AGEG5YR",
    "_AGE_G",
    "HTIN4",
    "HTM4",
    "WTKG3",
    "_BMI5",
    "_BMI5CAT",
    "_RFHLTH",
    "_PHYS14D",
    "_MENT14D",
    "_TOTINDA",
    "_MICHD",
    "_SMOKER3",
    "_RFSMOK3",
    "_RFDRHV9",
    "_RFBMI5",
    "_EDUCAG",
    "_INCOMG1",
    "_IMPRACE",
]


@dataclass(frozen=True)
class DatasetSpec:
    disease: str
    source: str
    label_rule: str
    builder: Callable[[pd.DataFrame], pd.DataFrame]


def main() -> int:
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    PROCESSED_DIR.mkdir(parents=True, exist_ok=True)

    brfss_path = prepare_brfss_xpt()
    nhanes_paths = prepare_nhanes_xpts()

    print(f"Reading BRFSS XPT: {brfss_path}")
    brfss = read_sas_selected(brfss_path, BRFSS_KEEP_COLUMNS)
    print(f"BRFSS rows={len(brfss):,}, columns={len(brfss.columns):,}")

    manifest: dict[str, object] = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "outputDir": str(PROCESSED_DIR),
        "datasets": {},
    }

    specs = [
        DatasetSpec("diabetes", BRFSS_XPT_ZIP_URL, "DIABETE4: 1 or 4 => 1, 3 => 0; other codes excluded", build_brfss_diabetes),
        DatasetSpec("heart", BRFSS_XPT_ZIP_URL, "_MICHD: 1 => 1, 2 => 0; fallback CVDINFR4/CVDCRHD4", build_brfss_heart),
        DatasetSpec("kidney", BRFSS_XPT_ZIP_URL, "CHCKDNY2: 1 => 1, 2 => 0; other codes excluded", build_brfss_kidney),
        DatasetSpec("stroke", BRFSS_XPT_ZIP_URL, "CVDSTRK3: 1 => 1, 2 => 0; other codes excluded", build_brfss_stroke),
    ]

    for spec in specs:
        output = spec.builder(brfss)
        info = write_dataset(
            spec.disease,
            output,
            spec.source,
            spec.label_rule,
            len(brfss),
            source_name="CDC BRFSS 2024",
            source_url="https://www.cdc.gov/brfss/annual_data/annual_2024.html",
            source_license="CDC public-use data",
        )
        manifest["datasets"][spec.disease] = info

    liver = build_liver_dataset(nhanes_paths)
    liver_info = write_dataset(
        "liver",
        liver,
        "NHANES 2017-March 2020 P_MCQ/P_BIOPRO/P_DEMO/P_BMX",
        "MCQ160L: 1 => 1, 2 => 0; other codes excluded",
        liver.attrs.get("raw_rows", len(liver)),
        source_name="CDC NHANES 2017-March 2020",
        source_url="https://wwwn.cdc.gov/nchs/nhanes/continuousnhanes/default.aspx?Cycle=2017-2020",
        source_license="CDC public-use data",
    )
    manifest["datasets"]["liver"] = liver_info

    mimic_infos = build_mimic_iv_datasets()
    manifest["datasets"].update(mimic_infos)

    if env_flag("MEDRISK_INCLUDE_SMALL_BENCHMARKS"):
        heart_uci = build_uci_heart_dataset()
        manifest["datasets"]["heart_uci"] = write_dataset(
            "heart_uci",
            heart_uci,
            "UCI Heart Disease processed Cleveland",
            "target: 0 => 0, 1-4 => 1",
            heart_uci.attrs.get("raw_rows", len(heart_uci)),
            disease_type="heart",
            display_name="UCI Heart Disease",
            source_name="UCI Machine Learning Repository",
            source_url=UCI_HEART_PAGE_URL,
            source_license="UCI dataset license / citation required",
        )

        kidney_uci = build_uci_ckd_dataset()
        manifest["datasets"]["kidney_uci"] = write_dataset(
            "kidney_uci",
            kidney_uci,
            "UCI Chronic Kidney Disease",
            "class: ckd => 1, notckd => 0",
            kidney_uci.attrs.get("raw_rows", len(kidney_uci)),
            disease_type="kidney",
            display_name="UCI Chronic Kidney Disease",
            source_name="UCI Machine Learning Repository",
            source_url=UCI_CKD_PAGE_URL,
            source_license="CC BY 4.0",
        )
    else:
        print("Skipping small UCI benchmark datasets; set MEDRISK_INCLUDE_SMALL_BENCHMARKS=1 to generate them.")

    manifest_path = PROCESSED_DIR / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    write_readme(manifest)
    print(f"Wrote manifest: {manifest_path}")
    return 0


def download(url: str, target: Path) -> None:
    if target.exists() and target.stat().st_size > 0:
        print(f"Using cached file: {target}")
        return
    tmp = target.with_suffix(target.suffix + ".tmp")
    if tmp.exists():
        tmp.unlink()
    print(f"Downloading {url}")
    with urllib.request.urlopen(url, timeout=120) as response, tmp.open("wb") as handle:
        shutil.copyfileobj(response, handle)
    tmp.replace(target)
    print(f"Downloaded {target} ({target.stat().st_size:,} bytes)")


def prepare_brfss_xpt() -> Path:
    zip_path = RAW_DIR / "LLCP2024XPT.zip"
    download(BRFSS_XPT_ZIP_URL, zip_path)
    extract_dir = RAW_DIR / "brfss_2024_xpt"
    extract_dir.mkdir(parents=True, exist_ok=True)
    xpt_files = sorted(extract_dir.glob("*.XPT")) + sorted(extract_dir.glob("*.xpt"))
    if not xpt_files:
        print(f"Extracting {zip_path}")
        with zipfile.ZipFile(zip_path) as archive:
            archive.extractall(extract_dir)
        xpt_files = sorted(extract_dir.glob("*.XPT")) + sorted(extract_dir.glob("*.xpt"))
    if not xpt_files:
        raise FileNotFoundError("BRFSS ZIP did not contain an XPT file")
    return xpt_files[0]


def prepare_nhanes_xpts() -> dict[str, Path]:
    paths: dict[str, Path] = {}
    for filename, url in NHANES_URLS.items():
        target = RAW_DIR / filename
        download(url, target)
        paths[filename] = target
    return paths


def normalize_columns(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    df.columns = [str(column).upper() for column in df.columns]
    return df


def read_sas_selected(path: Path, keep_columns: list[str], chunksize: int = 50_000) -> pd.DataFrame:
    """Read a large XPT/SAS file without materializing every source column."""
    wanted = {column.upper() for column in keep_columns}
    frames: list[pd.DataFrame] = []
    reader = pd.read_sas(path, format="xport", encoding="latin1", chunksize=chunksize)
    for index, chunk in enumerate(reader, start=1):
        chunk = normalize_columns(chunk)
        present = [column for column in chunk.columns if column in wanted]
        frames.append(chunk[present].copy())
        if index == 1 or index % 5 == 0:
            print(f"  BRFSS chunk {index}: rows={sum(len(frame) for frame in frames):,}, kept_columns={len(present)}")
    if not frames:
        return pd.DataFrame(columns=keep_columns)
    df = pd.concat(frames, ignore_index=True, copy=False)
    for column in keep_columns:
        if column not in df.columns:
            df[column] = pd.NA
    return df[[column for column in keep_columns if column in df.columns]]


def first_existing(df: pd.DataFrame, names: list[str]) -> pd.Series:
    for name in names:
        if name.upper() in df.columns:
            return df[name.upper()]
    return pd.Series([pd.NA] * len(df), index=df.index, dtype="Float64")


def yes_no(series: pd.Series) -> pd.Series:
    return series.map({1: 1, 2: 0})


def brfss_health_days(series: pd.Series) -> pd.Series:
    return series.replace({88: 0, 77: pd.NA, 99: pd.NA})


def brfss_bmi(series: pd.Series) -> pd.Series:
    return pd.to_numeric(series, errors="coerce") / 100.0


def brfss_weight_kg(series: pd.Series) -> pd.Series:
    return pd.to_numeric(series, errors="coerce") / 100.0


def brfss_binary_from_codes(series: pd.Series, yes_values: set[int], no_values: set[int]) -> pd.Series:
    numeric = pd.to_numeric(series, errors="coerce")
    result = pd.Series(pd.NA, index=series.index, dtype="Float64")
    result[numeric.isin(yes_values)] = 1
    result[numeric.isin(no_values)] = 0
    return result


def brfss_common_features(df: pd.DataFrame) -> pd.DataFrame:
    features = pd.DataFrame(index=df.index)
    features["age"] = first_existing(df, ["_AGE80"])
    features["ageGroup"] = first_existing(df, ["_AGEG5YR", "_AGE_G"])
    features["sex"] = first_existing(df, ["SEXVAR", "_SEX"]).map({1: 1, 2: 0})
    features["bmi"] = brfss_bmi(first_existing(df, ["_BMI5"]))
    features["bmiCategory"] = first_existing(df, ["_BMI5CAT"])
    features["heightInches"] = first_existing(df, ["HTIN4"])
    features["heightCm"] = first_existing(df, ["HTM4"])
    features["weightKg"] = brfss_weight_kg(first_existing(df, ["WTKG3"]))
    features["generalHealth"] = first_existing(df, ["GENHLTH"]).replace({7: pd.NA, 9: pd.NA})
    features["physicalHealthDays"] = brfss_health_days(first_existing(df, ["PHYSHLTH"]))
    features["mentalHealthDays"] = brfss_health_days(first_existing(df, ["MENTHLTH"]))
    features["checkupTime"] = first_existing(df, ["CHECKUP1"]).replace({7: pd.NA, 8: pd.NA, 9: pd.NA})
    features["exercise"] = yes_no(first_existing(df, ["EXERANY2"]))
    features["physicalActivity"] = first_existing(df, ["_TOTINDA"]).map({1: 1, 2: 0})
    features["smoked100"] = yes_no(first_existing(df, ["SMOKE100"]))
    features["smokerCategory"] = first_existing(df, ["_SMOKER3"]).replace({9: pd.NA})
    features["currentSmoker"] = first_existing(df, ["SMOKDAY2"]).map({1: 1, 2: 1, 3: 0})
    features["alcoholAny"] = yes_no(first_existing(df, ["DRNKANY6"]))
    features["heavyDrinking"] = first_existing(df, ["_RFDRHV9"]).map({1: 0, 2: 1})
    features["averageDrinks"] = first_existing(df, ["AVEDRNK4"]).replace({77: pd.NA, 99: pd.NA})
    features["bingeDrinkDays"] = first_existing(df, ["DRNK3GE5"]).replace({88: 0, 77: pd.NA, 99: pd.NA})
    features["maxDrinks"] = first_existing(df, ["MAXDRNKS"]).replace({77: pd.NA, 99: pd.NA})
    features["hasPersonalDoctor"] = first_existing(df, ["PERSDOC3"]).map({1: 1, 2: 1, 3: 0})
    features["couldNotSeeDoctorCost"] = yes_no(first_existing(df, ["MEDCOST1"]))
    features["insuranceType"] = first_existing(df, ["PRIMINS2"]).replace({77: pd.NA, 99: pd.NA})
    features["education"] = first_existing(df, ["EDUCA"]).replace({9: pd.NA})
    features["educationGroup"] = first_existing(df, ["_EDUCAG"]).replace({9: pd.NA})
    features["income"] = first_existing(df, ["INCOME3"]).replace({77: pd.NA, 99: pd.NA})
    features["incomeGroup"] = first_existing(df, ["_INCOMG1"]).replace({9: pd.NA})
    features["race"] = first_existing(df, ["_IMPRACE"]).replace({9: pd.NA})
    features["deaf"] = yes_no(first_existing(df, ["DEAF"]))
    features["blind"] = yes_no(first_existing(df, ["BLIND"]))
    features["cognitiveDifficulty"] = yes_no(first_existing(df, ["DECIDE"]))
    features["difficultyWalking"] = yes_no(first_existing(df, ["DIFFWALK"]))
    features["difficultyDressing"] = yes_no(first_existing(df, ["DIFFDRES"]))
    features["difficultyErrands"] = yes_no(first_existing(df, ["DIFFALON"]))
    features["highBloodPressure"] = brfss_binary_from_codes(first_existing(df, ["BPHIGH6"]), {1}, {3})
    features["highCholesterol"] = yes_no(first_existing(df, ["TOLDHI3"]))
    features["cholesterolCheck"] = brfss_binary_from_codes(first_existing(df, ["CHOLCHK3"]), {1, 2, 3, 4}, {8})
    features["heartDiseaseHistory"] = brfss_binary_from_codes(first_existing(df, ["_MICHD"]), {1}, {2})
    features["strokeHistory"] = yes_no(first_existing(df, ["CVDSTRK3"]))
    features["kidneyDiseaseHistory"] = yes_no(first_existing(df, ["CHCKDNY2"]))
    features["diabetesHistory"] = brfss_binary_from_codes(first_existing(df, ["DIABETE4"]), {1, 4}, {3})
    return clean_feature_frame(features)


def clean_feature_frame(features: pd.DataFrame) -> pd.DataFrame:
    cleaned = features.copy()
    for column in cleaned.columns:
        cleaned[column] = pd.to_numeric(cleaned[column], errors="coerce")
        if cleaned[column].notna().any():
            cleaned[column] = cleaned[column].fillna(cleaned[column].median())
        else:
            cleaned[column] = 0
    return cleaned


def attach_label(features: pd.DataFrame, label: pd.Series, exclude_columns: list[str]) -> pd.DataFrame:
    mask = label.notna()
    output = features.loc[mask].drop(columns=[column for column in exclude_columns if column in features.columns], errors="ignore").copy()
    output["label"] = label.loc[mask].astype(int)
    return output


def build_brfss_diabetes(df: pd.DataFrame) -> pd.DataFrame:
    features = brfss_common_features(df)
    label = brfss_binary_from_codes(first_existing(df, ["DIABETE4"]), {1, 4}, {3})
    return attach_label(features, label, ["diabetesHistory"])


def build_brfss_heart(df: pd.DataFrame) -> pd.DataFrame:
    features = brfss_common_features(df)
    label = brfss_binary_from_codes(first_existing(df, ["_MICHD"]), {1}, {2})
    if label.notna().sum() == 0:
        heart_attack = first_existing(df, ["CVDINFR4"])
        coronary = first_existing(df, ["CVDCRHD4"])
        positive = (heart_attack == 1) | (coronary == 1)
        negative = (heart_attack == 2) & (coronary == 2)
        label = pd.Series(pd.NA, index=df.index, dtype="Float64")
        label[positive] = 1
        label[negative] = 0
    return attach_label(features, label, ["heartDiseaseHistory"])


def build_brfss_kidney(df: pd.DataFrame) -> pd.DataFrame:
    features = brfss_common_features(df)
    label = yes_no(first_existing(df, ["CHCKDNY2"]))
    return attach_label(features, label, ["kidneyDiseaseHistory"])


def build_brfss_stroke(df: pd.DataFrame) -> pd.DataFrame:
    features = brfss_common_features(df)
    label = yes_no(first_existing(df, ["CVDSTRK3"]))
    return attach_label(features, label, ["strokeHistory"])


def build_liver_dataset(paths: dict[str, Path]) -> pd.DataFrame:
    mcq = normalize_columns(pd.read_sas(paths["P_MCQ.XPT"], format="xport", encoding="latin1"))
    biopro = normalize_columns(pd.read_sas(paths["P_BIOPRO.XPT"], format="xport", encoding="latin1"))
    demo = normalize_columns(pd.read_sas(paths["P_DEMO.XPT"], format="xport", encoding="latin1"))
    bmx = normalize_columns(pd.read_sas(paths["P_BMX.XPT"], format="xport", encoding="latin1"))
    raw_rows = len(mcq)
    merged = mcq[["SEQN", "MCQ160L"]].merge(demo, on="SEQN", how="left").merge(bmx, on="SEQN", how="left").merge(biopro, on="SEQN", how="left")
    label = merged["MCQ160L"].map({1: 1, 2: 0})
    features = pd.DataFrame(index=merged.index)
    features["age"] = first_existing(merged, ["RIDAGEYR"])
    features["sex"] = first_existing(merged, ["RIAGENDR"]).map({1: 1, 2: 0})
    features["race"] = first_existing(merged, ["RIDRETH3", "RIDRETH1"])
    features["education"] = first_existing(merged, ["DMDEDUC2", "DMDEDUC3"]).replace({7: pd.NA, 9: pd.NA, 77: pd.NA, 99: pd.NA})
    features["incomePovertyRatio"] = first_existing(merged, ["INDFMPIR"])
    features["bmi"] = first_existing(merged, ["BMXBMI"])
    features["weightKg"] = first_existing(merged, ["BMXWT"])
    features["heightCm"] = first_existing(merged, ["BMXHT"])
    features["waistCm"] = first_existing(merged, ["BMXWAIST"])
    features["alt"] = first_existing(merged, ["LBXSATSI", "LBXSALT"])
    features["ast"] = first_existing(merged, ["LBXSASSI", "LBXSAST"])
    features["totalBilirubin"] = first_existing(merged, ["LBDSTBSI", "LBXSTB"])
    features["albumin"] = first_existing(merged, ["LBDSALSI", "LBXSAL"])
    features["alkalinePhosphatase"] = first_existing(merged, ["LBXSAPSI", "LBXSAP"])
    features["ggt"] = first_existing(merged, ["LBXSGTSI", "LBXSGT"])
    features["totalProtein"] = first_existing(merged, ["LBDSTPSI", "LBXSTP"])
    features["glucose"] = first_existing(merged, ["LBDSGLSI", "LBXSGL"])
    features["creatinine"] = first_existing(merged, ["LBDSCRSI", "LBXSCR"])
    features["bun"] = first_existing(merged, ["LBDSBUSI", "LBXSBU"])
    features = clean_feature_frame(features)
    output = attach_label(features, label, [])
    output.attrs["raw_rows"] = raw_rows
    return output


def env_flag(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def build_mimic_iv_datasets() -> dict[str, object]:
    root = find_mimic_iv_root()
    if root is None:
        print("Skipping MIMIC-IV: set MEDRISK_MIMIC_IV_DIR to an authorized local MIMIC-IV v3.1 directory.")
        return {}

    print(f"Reading MIMIC-IV from {root}")
    admissions = read_mimic_csv(root, "hosp/admissions.csv", [
        "subject_id",
        "hadm_id",
        "admittime",
        "admission_type",
        "admission_location",
        "insurance",
        "language",
        "marital_status",
        "race",
    ])
    patients = read_mimic_csv(root, "hosp/patients.csv", ["subject_id", "gender", "anchor_age"])
    diagnoses = read_mimic_csv(root, "hosp/diagnoses_icd.csv", ["hadm_id", "icd_code", "icd_version"])
    features = build_mimic_features(admissions, patients)
    results: dict[str, object] = {}
    disease_specs = {
        "diabetes": ("MIMIC-IV Diabetes", "ICD-9 250* or ICD-10 E10/E11/E13/E14"),
        "heart": ("MIMIC-IV Heart Disease", "ICD-9 410-414/428 or ICD-10 I20-I25/I50"),
        "kidney": ("MIMIC-IV Chronic Kidney Disease", "ICD-9 585/586 or ICD-10 N18/N19"),
        "liver": ("MIMIC-IV Liver Disease", "ICD-9 570-573 or ICD-10 K70-K77"),
        "stroke": ("MIMIC-IV Stroke", "ICD-9 430-438 or ICD-10 I60-I69/G45"),
    }
    for disease_type, (display_name, label_rule) in disease_specs.items():
        label_map = mimic_labels(diagnoses, disease_type)
        merged = features.merge(label_map, on="hadm_id", how="left")
        labels = merged["label"].fillna(0).astype(int)
        feature_frame = clean_feature_frame(merged.drop(columns=["hadm_id", "label"], errors="ignore"))
        output = feature_frame.copy()
        output["label"] = labels
        if len(output) < 1000 or output["label"].nunique() < 2:
            print(f"Skipping MIMIC-IV {disease_type}: insufficient rows or only one label class.")
            continue
        dataset_id = f"mimic_iv_{disease_type}"
        results[dataset_id] = write_dataset(
            dataset_id,
            output,
            "MIMIC-IV v3.1 hosp/admissions + patients + diagnoses_icd",
            label_rule,
            len(admissions),
            disease_type=disease_type,
            display_name=display_name,
            source_name="PhysioNet MIMIC-IV v3.1",
            source_url=MIMIC_IV_PAGE_URL,
            source_license="PhysioNet credentialed health data license",
        )
    return results


def find_mimic_iv_root() -> Path | None:
    candidates: list[Path] = []
    configured = os.getenv("MEDRISK_MIMIC_IV_DIR")
    if configured:
        candidates.append(Path(configured).expanduser())
    candidates.extend([
        RAW_DIR / "mimic-iv",
        RAW_DIR / "mimiciv",
        RAW_DIR / "mimic-iv-3.1",
    ])
    nested_names = ["", "mimic-iv-3.1", "mimiciv", "mimic-iv"]
    for base in candidates:
        for name in nested_names:
            candidate = (base / name).resolve() if name else base.resolve()
            if mimic_file(candidate, "hosp/admissions.csv") and mimic_file(candidate, "hosp/patients.csv") and mimic_file(candidate, "hosp/diagnoses_icd.csv"):
                return candidate
    return None


def mimic_file(root: Path, relative: str) -> Path | None:
    plain = root / relative
    if plain.exists():
        return plain
    gz = plain.with_suffix(plain.suffix + ".gz")
    if gz.exists():
        return gz
    return None


def read_mimic_csv(root: Path, relative: str, usecols: list[str]) -> pd.DataFrame:
    path = mimic_file(root, relative)
    if path is None:
        raise FileNotFoundError(f"MIMIC-IV file not found: {relative}")
    df = pd.read_csv(path, usecols=lambda column: str(column).lower() in usecols, compression="infer", low_memory=False)
    df.columns = [str(column).lower() for column in df.columns]
    missing = [column for column in usecols if column not in df.columns]
    if missing:
        raise ValueError(f"{relative} missing columns: {', '.join(missing)}")
    return df[usecols]


def build_mimic_features(admissions: pd.DataFrame, patients: pd.DataFrame) -> pd.DataFrame:
    admissions = admissions.copy()
    patients = patients.copy()
    admissions["subject_id"] = admissions["subject_id"].astype(str)
    admissions["hadm_id"] = admissions["hadm_id"].astype(str)
    admissions["admittime_dt"] = pd.to_datetime(admissions["admittime"], errors="coerce")
    admissions = admissions.sort_values(["subject_id", "admittime_dt", "hadm_id"]).reset_index(drop=True)
    admissions["prevAdmissionCount"] = admissions.groupby("subject_id").cumcount()
    patients["subject_id"] = patients["subject_id"].astype(str)
    merged = admissions.merge(patients[["subject_id", "gender", "anchor_age"]], on="subject_id", how="left")
    features = pd.DataFrame()
    features["hadm_id"] = merged["hadm_id"]
    features["age"] = pd.to_numeric(merged["anchor_age"], errors="coerce")
    features["sex"] = merged["gender"].astype(str).str.upper().map({"M": 1, "F": 0})
    features["prevAdmissionCount"] = merged["prevAdmissionCount"]
    features["admissionYear"] = merged["admittime_dt"].dt.year
    for column in ["admission_type", "admission_location", "insurance", "language", "marital_status", "race"]:
        features[column] = encode_category(merged[column])
    return features.drop_duplicates(subset=["hadm_id"]).reset_index(drop=True)


def encode_category(series: pd.Series) -> pd.Series:
    normalized = series.fillna("UNKNOWN").astype(str).str.strip().str.upper().replace({"": "UNKNOWN"})
    return normalized.astype("category").cat.codes


def mimic_labels(diagnoses: pd.DataFrame, disease_type: str) -> pd.DataFrame:
    dx = diagnoses.copy()
    dx["hadm_id"] = dx["hadm_id"].astype(str)
    code = dx["icd_code"].fillna("").astype(str).str.replace(".", "", regex=False).str.upper()
    version = pd.to_numeric(dx["icd_version"], errors="coerce").fillna(0).astype(int)
    icd9_num = pd.to_numeric(code.str.extract(r"^(\d{3})", expand=False), errors="coerce")
    if disease_type == "diabetes":
        positive = ((version == 9) & code.str.startswith("250")) | ((version == 10) & code.str.startswith(("E10", "E11", "E13", "E14")))
    elif disease_type == "heart":
        positive = ((version == 9) & (icd9_num.between(410, 414) | (icd9_num == 428))) | ((version == 10) & code.str.startswith(("I20", "I21", "I22", "I23", "I24", "I25", "I50")))
    elif disease_type == "kidney":
        positive = ((version == 9) & icd9_num.isin([585, 586])) | ((version == 10) & code.str.startswith(("N18", "N19")))
    elif disease_type == "liver":
        positive = ((version == 9) & icd9_num.between(570, 573)) | ((version == 10) & code.str.startswith(("K70", "K71", "K72", "K73", "K74", "K75", "K76", "K77")))
    elif disease_type == "stroke":
        positive = ((version == 9) & icd9_num.between(430, 438)) | ((version == 10) & code.str.startswith(("I60", "I61", "I62", "I63", "I64", "I65", "I66", "I67", "I68", "I69", "G45")))
    else:
        positive = pd.Series(False, index=dx.index)
    labels = pd.DataFrame({"hadm_id": dx["hadm_id"], "label": positive.astype(int)})
    return labels.groupby("hadm_id", as_index=False)["label"].max()


def build_uci_heart_dataset() -> pd.DataFrame:
    zip_path = RAW_DIR / "uci_heart_disease.zip"
    download(UCI_HEART_ZIP_URL, zip_path)
    extract_dir = RAW_DIR / "uci_heart_disease"
    extract_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path) as archive:
        archive.extractall(extract_dir)
    candidates = list(extract_dir.rglob("processed.cleveland.data"))
    if not candidates:
        candidates = [path for path in extract_dir.rglob("*") if path.is_file() and "cleveland" in path.name.lower()]
    if not candidates:
        raise FileNotFoundError("UCI Heart Disease ZIP did not contain processed Cleveland data")
    columns = [
        "age", "sex", "chestPainType", "restingBloodPressure", "cholesterol", "fastingBloodSugar",
        "restingEcg", "maxHeartRate", "exerciseAngina", "oldpeak", "slope", "majorVessels", "thal", "target",
    ]
    raw = pd.read_csv(candidates[0], names=columns, na_values="?")
    label = pd.to_numeric(raw["target"], errors="coerce").map(lambda value: 1 if value and value > 0 else 0)
    features = raw.drop(columns=["target"]).apply(pd.to_numeric, errors="coerce")
    features = clean_feature_frame(features)
    output = attach_label(features, label, [])
    output.attrs["raw_rows"] = len(raw)
    return output


def build_uci_ckd_dataset() -> pd.DataFrame:
    zip_path = RAW_DIR / "uci_chronic_kidney_disease.zip"
    download(UCI_CKD_ZIP_URL, zip_path)
    extract_dir = RAW_DIR / "uci_chronic_kidney_disease"
    extract_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path) as archive:
        archive.extractall(extract_dir)
    candidates = sorted(extract_dir.rglob("*.arff"))
    if not candidates:
        fallback = RAW_DIR / "chronic_kidney_disease.arff"
        download(UCI_CKD_ARFF_FALLBACK_URL, fallback)
        candidates = [fallback]
    attrs: list[str] = []
    rows: list[list[str]] = []
    in_data = False
    for raw_line in candidates[0].read_text(encoding="utf-8", errors="ignore").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("%"):
            continue
        lower = line.lower()
        if lower.startswith("@attribute"):
            parts = line.split(maxsplit=2)
            if len(parts) >= 2:
                attrs.append(parts[1].strip("'\""))
        elif lower.startswith("@data"):
            in_data = True
        elif in_data:
            rows.extend(csv.reader([line]))
    if not attrs or not rows:
        raise ValueError("UCI CKD ARFF file could not be parsed")
    raw = pd.DataFrame(rows, columns=attrs[: len(rows[0])])
    raw = raw.replace({"?": pd.NA, "\t?": pd.NA})
    label_column = "class" if "class" in raw.columns else raw.columns[-1]
    label = raw[label_column].astype(str).str.strip().str.replace("\t", "", regex=False).map({"ckd": 1, "notckd": 0})
    features = raw.drop(columns=[label_column]).copy()
    for column in features.columns:
        numeric = pd.to_numeric(features[column], errors="coerce")
        if numeric.notna().mean() >= 0.7:
            features[column] = numeric.fillna(numeric.median())
        else:
            features[column] = features[column].astype(str).str.strip().replace({"<NA>": "unknown", "nan": "unknown", "": "unknown"}).fillna("unknown")
    output = attach_label(features, label, [])
    output.attrs["raw_rows"] = len(raw)
    return output


def split_train_eval(df: pd.DataFrame, eval_fraction: float = 0.2) -> tuple[pd.DataFrame, pd.DataFrame]:
    eval_parts = []
    for _, group in df.groupby("label", dropna=False):
        eval_count = max(1, int(round(len(group) * eval_fraction)))
        eval_parts.append(group.sample(n=min(eval_count, len(group)), random_state=42))
    eval_df = pd.concat(eval_parts).sort_index()
    train_df = df.drop(index=eval_df.index)
    return train_df.reset_index(drop=True), eval_df.reset_index(drop=True)


def write_dataset(
    dataset_id: str,
    df: pd.DataFrame,
    source: str,
    label_rule: str,
    raw_rows: int,
    disease_type: str | None = None,
    display_name: str | None = None,
    source_name: str | None = None,
    source_url: str | None = None,
    source_license: str | None = None,
) -> dict[str, object]:
    if "label" not in df.columns:
        raise ValueError(f"{dataset_id} dataset has no label column")
    feature_columns = [column for column in df.columns if column != "label"]
    df = df[feature_columns + ["label"]]
    output = PROCESSED_DIR / f"{dataset_id}.csv"
    train_df, eval_df = split_train_eval(df)
    train_output = PROCESSED_DIR / f"{dataset_id}_train.csv"
    eval_output = PROCESSED_DIR / f"{dataset_id}_eval.csv"
    df.to_csv(output, index=False, encoding="utf-8")
    train_df.to_csv(train_output, index=False, encoding="utf-8")
    eval_df.to_csv(eval_output, index=False, encoding="utf-8")
    label_counts = {str(int(key)): int(value) for key, value in df["label"].value_counts().sort_index().items()}
    info = {
        "datasetId": dataset_id,
        "diseaseType": disease_type or dataset_id,
        "displayName": display_name or dataset_id,
        "file": str(output),
        "trainFile": str(train_output),
        "evaluationFile": str(eval_output),
        "source": source,
        "sourceName": source_name or source,
        "sourceUrl": source_url or source,
        "sourceLicense": source_license or "Public-use data",
        "labelRule": label_rule,
        "rawRows": int(raw_rows),
        "rows": int(len(df)),
        "trainRows": int(len(train_df)),
        "evaluationRows": int(len(eval_df)),
        "positiveRows": int(label_counts.get("1", 0)),
        "negativeRows": int(label_counts.get("0", 0)),
        "featureCount": len(feature_columns),
        "featureColumns": feature_columns,
    }
    print(f"Wrote {output}: rows={info['rows']:,}, positives={info['positiveRows']:,}, negatives={info['negativeRows']:,}, features={info['featureCount']}")
    return info


def write_readme(manifest: dict[str, object]) -> None:
    lines = [
        "# MedRisk processed training datasets",
        "",
        "这些 CSV 由 `scripts/prepare-large-datasets.ps1` 自动生成，可在管理后台上传。",
        "",
        "| 文件 | 管理后台病种值 | 训练行数 | 评估行数 | 阳性 | 阴性 | 来源 |",
        "|---|---|---:|---:|---:|---:|---|",
    ]
    datasets = manifest.get("datasets", {})
    if isinstance(datasets, dict):
        for disease, info in datasets.items():
            if isinstance(info, dict):
                lines.append(
                    f"| `{info.get('datasetId', disease)}.csv` | `{info.get('diseaseType', disease)}` | {info.get('trainRows', 0)} | {info.get('evaluationRows', 0)} | {info.get('positiveRows', 0)} | {info.get('negativeRows', 0)} | {info.get('sourceName', info.get('source', ''))} |"
                )
    lines.extend(
        [
            "",
            "说明：数据仅用于教学演示训练，不构成临床诊断依据；上传前可按需要再抽样或脱敏审查。",
        ]
    )
    (PROCESSED_DIR / "README.md").write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise
