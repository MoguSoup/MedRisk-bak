# MedRisk processed training datasets

这些 CSV 由 `scripts/prepare-large-datasets.ps1` 自动生成，可在管理后台上传。

| 文件 | 管理后台病种值 | 行数 | 阳性 | 阴性 |
|---|---|---:|---:|---:|
| `diabetes.csv` | `diabetes` | 453241 | 77116 | 376125 |
| `heart.csv` | `heart` | 452464 | 42338 | 410126 |
| `kidney.csv` | `kidney` | 455691 | 23752 | 431939 |
| `stroke.csv` | `stroke` | 456218 | 20661 | 435557 |
| `liver.csv` | `liver` | 9213 | 462 | 8751 |

说明：数据仅用于教学演示训练，不构成临床诊断依据；上传前可按需要再抽样或脱敏审查。