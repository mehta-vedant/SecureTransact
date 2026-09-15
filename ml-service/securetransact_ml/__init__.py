"""SecureTransact ML Risk Scoring Service.

Statistical anomaly detection + behavioral profiling.
This module exposes a Flask API (POST /score) called by the Java backend
via MlClientConfig (RestClient) with a 500ms timeout."""
