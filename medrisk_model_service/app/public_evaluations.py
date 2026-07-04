from __future__ import annotations

from typing import Any


PUBLIC_EVALUATIONS: dict[str, dict[str, Any]] = {
    "diabetes": {
        "evaluationDataset": "CDC BRFSS 2024 Diabetes",
        "datasetSource": "CDC BRFSS 2024",
        "datasetUrl": "https://www.cdc.gov/brfss/annual_data/annual_2024.html",
        "datasetLicense": "CDC public-use data",
        "sampleCount": 453241,
        "positiveRows": 77116,
        "validationType": "held-out public survey evaluation",
    },
    "heart": {
        "evaluationDataset": "CDC BRFSS 2024 Heart Disease",
        "datasetSource": "CDC BRFSS 2024",
        "datasetUrl": "https://www.cdc.gov/brfss/annual_data/annual_2024.html",
        "datasetLicense": "CDC public-use data",
        "sampleCount": 452464,
        "positiveRows": 42338,
        "validationType": "held-out public survey evaluation",
    },
    "kidney": {
        "evaluationDataset": "CDC BRFSS 2024 Chronic Kidney Disease",
        "datasetSource": "CDC BRFSS 2024",
        "datasetUrl": "https://www.cdc.gov/brfss/annual_data/annual_2024.html",
        "datasetLicense": "CDC public-use data",
        "sampleCount": 455691,
        "positiveRows": 23752,
        "validationType": "held-out public survey evaluation",
    },
    "stroke": {
        "evaluationDataset": "CDC BRFSS 2024 Stroke",
        "datasetSource": "CDC BRFSS 2024",
        "datasetUrl": "https://www.cdc.gov/brfss/annual_data/annual_2024.html",
        "datasetLicense": "CDC public-use data",
        "sampleCount": 456218,
        "positiveRows": 20661,
        "validationType": "held-out public survey evaluation",
    },
    "liver": {
        "evaluationDataset": "CDC NHANES 2017-March 2020 Liver",
        "datasetSource": "CDC NHANES 2017-March 2020",
        "datasetUrl": "https://wwwn.cdc.gov/nchs/nhanes/continuousnhanes/default.aspx?Cycle=2017-2020",
        "datasetLicense": "CDC public-use data",
        "sampleCount": 9213,
        "positiveRows": 462,
        "validationType": "held-out public examination/laboratory evaluation",
    },
}


def public_evaluation(disease_type: str) -> dict[str, Any]:
    return dict(PUBLIC_EVALUATIONS.get(disease_type, {}))
