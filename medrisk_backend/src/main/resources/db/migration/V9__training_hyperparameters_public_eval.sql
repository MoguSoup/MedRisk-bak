ALTER TABLE model_training_jobs ADD COLUMN evaluation_dataset_id BIGINT;
ALTER TABLE model_training_jobs ADD COLUMN hyperparameters_json JSON;

ALTER TABLE model_versions ADD COLUMN hyperparameters_json JSON;
ALTER TABLE model_versions ADD COLUMN evaluation_dataset_name VARCHAR(255);
ALTER TABLE model_versions ADD COLUMN evaluation_dataset_source VARCHAR(255);
ALTER TABLE model_versions ADD COLUMN evaluation_dataset_url VARCHAR(500);

CREATE INDEX idx_model_training_jobs_eval_dataset ON model_training_jobs(evaluation_dataset_id);

UPDATE model_versions
SET hyperparameters_json = '{"nEstimators":80,"maxDepth":3,"learningRate":0.05,"subsample":0.9,"colsampleBytree":0.9,"regLambda":1.0,"minChildWeight":1.0}',
    evaluation_dataset_name = 'CDC BRFSS 2024 Diabetes',
    evaluation_dataset_source = 'CDC BRFSS 2024',
    evaluation_dataset_url = 'https://www.cdc.gov/brfss/annual_data/annual_2024.html'
WHERE version = 'diabetes-xgb-teaching-v1.0.0';

UPDATE model_versions
SET hyperparameters_json = '{"nEstimators":80,"maxDepth":3,"learningRate":0.05,"subsample":0.9,"colsampleBytree":0.9,"regLambda":1.0,"minChildWeight":1.0}',
    evaluation_dataset_name = 'CDC BRFSS 2024 Heart Disease',
    evaluation_dataset_source = 'CDC BRFSS 2024',
    evaluation_dataset_url = 'https://www.cdc.gov/brfss/annual_data/annual_2024.html'
WHERE version = 'heart-xgb-teaching-v1.0.0';

UPDATE model_versions
SET hyperparameters_json = '{"nEstimators":80,"maxDepth":3,"learningRate":0.05,"subsample":0.9,"colsampleBytree":0.9,"regLambda":1.0,"minChildWeight":1.0}',
    evaluation_dataset_name = 'CDC BRFSS 2024 Chronic Kidney Disease',
    evaluation_dataset_source = 'CDC BRFSS 2024',
    evaluation_dataset_url = 'https://www.cdc.gov/brfss/annual_data/annual_2024.html'
WHERE version = 'kidney-lightgbm-teaching-v1.0.0';

UPDATE model_versions
SET hyperparameters_json = '{"nEstimators":80,"maxDepth":3,"learningRate":0.05,"subsample":0.9,"colsampleBytree":0.9,"regLambda":1.0,"minChildWeight":1.0}',
    evaluation_dataset_name = 'CDC NHANES 2017-March 2020 Liver',
    evaluation_dataset_source = 'CDC NHANES 2017-March 2020',
    evaluation_dataset_url = 'https://wwwn.cdc.gov/nchs/nhanes/continuousnhanes/default.aspx?Cycle=2017-2020'
WHERE version = 'liver-catboost-teaching-v1.0.0';

UPDATE model_versions
SET hyperparameters_json = '{"nEstimators":80,"maxDepth":3,"learningRate":0.05,"subsample":0.9,"colsampleBytree":0.9,"regLambda":1.0,"minChildWeight":1.0}',
    evaluation_dataset_name = 'CDC BRFSS 2024 Stroke',
    evaluation_dataset_source = 'CDC BRFSS 2024',
    evaluation_dataset_url = 'https://www.cdc.gov/brfss/annual_data/annual_2024.html'
WHERE version = 'stroke-rf-teaching-v1.0.0';
