from __future__ import annotations

import json
import shutil
import sys
import urllib.request
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
RAW_DIR = ROOT / "data" / "raw"
PROCESSED_DIR = ROOT / "data" / "processed"

BRFSS_XPT_ZIP_URL = "https://www.cdc.gov/brfss/annual_data/2024/files/LLCP2024XPT.zip"
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
    brfss = normalize_columns(pd.read_sas(brfss_path, format="xport", encoding="latin1"))
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
        info = write_dataset(spec.disease, output, spec.source, spec.label_rule, len(brfss))
        manifest["datasets"][spec.disease] = info

    liver = build_liver_dataset(nhanes_paths)
    liver_info = write_dataset(
        "liver",
        liver,
        "NHANES 2017-March 2020 P_MCQ/P_BIOPRO/P_DEMO/P_BMX",
        "MCQ160L: 1 => 1, 2 => 0; other codes excluded",
        liver.attrs.get("raw_rows", len(liver)),
    )
    manifest["datasets"]["liver"] = liver_info

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


def write_dataset(disease: str, df: pd.DataFrame, source: str, label_rule: str, raw_rows: int) -> dict[str, object]:
    if "label" not in df.columns:
        raise ValueError(f"{disease} dataset has no label column")
    feature_columns = [column for column in df.columns if column != "label"]
    df = df[feature_columns + ["label"]]
    output = PROCESSED_DIR / f"{disease}.csv"
    df.to_csv(output, index=False, encoding="utf-8")
    label_counts = {str(int(key)): int(value) for key, value in df["label"].value_counts().sort_index().items()}
    info = {
        "file": str(output),
        "source": source,
        "labelRule": label_rule,
        "rawRows": int(raw_rows),
        "rows": int(len(df)),
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
        "| 文件 | 管理后台病种值 | 行数 | 阳性 | 阴性 |",
        "|---|---|---:|---:|---:|",
    ]
    datasets = manifest.get("datasets", {})
    if isinstance(datasets, dict):
        for disease, info in datasets.items():
            if isinstance(info, dict):
                lines.append(
                    f"| `{disease}.csv` | `{disease}` | {info.get('rows', 0)} | {info.get('positiveRows', 0)} | {info.get('negativeRows', 0)} |"
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
