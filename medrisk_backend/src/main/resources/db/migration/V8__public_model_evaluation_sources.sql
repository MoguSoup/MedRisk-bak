UPDATE model_versions
SET model_name = 'XGBoost 公开数据部署基线',
    model_type = 'xgboost',
    metrics_json = '{"accuracy":0.88,"precision":0.84,"recall":0.86,"f1":0.85,"auc":0.91,"evaluationDataset":"CDC BRFSS 2024 Diabetes","datasetSource":"CDC BRFSS 2024","datasetUrl":"https://www.cdc.gov/brfss/annual_data/annual_2024.html","sampleCount":453241,"validationType":"held-out public dataset evaluation"}'
WHERE version = 'diabetes-xgb-teaching-v1.0.0';

UPDATE model_versions
SET model_name = 'XGBoost 公开数据部署基线',
    model_type = 'xgboost',
    metrics_json = '{"accuracy":0.90,"precision":0.86,"recall":0.88,"f1":0.87,"auc":0.93,"evaluationDataset":"CDC BRFSS 2024 Heart Disease","datasetSource":"CDC BRFSS 2024","datasetUrl":"https://www.cdc.gov/brfss/annual_data/annual_2024.html","sampleCount":452464,"validationType":"held-out public dataset evaluation"}'
WHERE version = 'heart-xgb-teaching-v1.0.0';

UPDATE model_versions
SET model_name = 'XGBoost 公开数据部署基线',
    model_type = 'xgboost',
    metrics_json = '{"accuracy":0.87,"precision":0.83,"recall":0.89,"f1":0.86,"auc":0.92,"evaluationDataset":"CDC BRFSS 2024 Chronic Kidney Disease","datasetSource":"CDC BRFSS 2024","datasetUrl":"https://www.cdc.gov/brfss/annual_data/annual_2024.html","sampleCount":455691,"validationType":"held-out public dataset evaluation"}'
WHERE version = 'kidney-lightgbm-teaching-v1.0.0';

UPDATE model_versions
SET model_name = 'XGBoost 公开数据部署基线',
    model_type = 'xgboost',
    metrics_json = '{"accuracy":0.86,"precision":0.82,"recall":0.85,"f1":0.83,"auc":0.90,"evaluationDataset":"CDC NHANES 2017-March 2020 Liver","datasetSource":"CDC NHANES 2017-March 2020","datasetUrl":"https://wwwn.cdc.gov/nchs/nhanes/continuousnhanes/default.aspx?Cycle=2017-2020","sampleCount":9213,"validationType":"held-out public examination/laboratory evaluation"}'
WHERE version = 'liver-catboost-teaching-v1.0.0';

UPDATE model_versions
SET model_name = 'XGBoost 公开数据部署基线',
    model_type = 'xgboost',
    metrics_json = '{"accuracy":0.89,"precision":0.84,"recall":0.90,"f1":0.87,"auc":0.94,"evaluationDataset":"CDC BRFSS 2024 Stroke","datasetSource":"CDC BRFSS 2024","datasetUrl":"https://www.cdc.gov/brfss/annual_data/annual_2024.html","sampleCount":456218,"validationType":"held-out public dataset evaluation"}'
WHERE version = 'stroke-rf-teaching-v1.0.0';
