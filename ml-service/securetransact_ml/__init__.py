"""SecureTransact ML Risk Scoring Service.

Statistical anomaly detection + behavioral profiling.
This module exposes a standalone Flask API (POST /score). It is not currently
wired into the Java backend's live decision path (see ml-service/README)."""
