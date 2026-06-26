# MedRisk Demo Data

This directory keeps small, safe demo files that collaborators can pull from the private repository and use immediately.

Tracked files:

- `medrisk_sample.csv`: compact teaching sample for prediction and batch workflows.
- `medrisk_demo_cases.csv`: three synthetic demo patients for low, medium, and high risk examples.
- `medrisk_batch_template.csv`: upload template for batch prediction.
- `processed/README.md`: generation notes for large public-derived training datasets.

Ignored files:

- `raw/`: downloaded public raw datasets such as BRFSS/NHANES exports.
- `processed/*.csv`: large generated training datasets. Recreate them with `scripts/prepare-large-datasets.ps1` when needed.
- Local H2 databases under `medrisk_backend/data/`.
- Runtime uploads under `uploads/` or `medrisk_backend/uploads/`.

Demo login accounts are not stored as a committed database file. They are seeded idempotently by the Spring Boot application on startup:

- `admin / 123456`
- `doctor / 123456`
- `patient / 123456`

Keeping the database generated from code avoids committing local audit logs, uploaded avatars, question history, or machine-specific H2 files while still letting collaborators log in normally after startup.
