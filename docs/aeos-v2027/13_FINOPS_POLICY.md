# 13_FINOPS_POLICY

## STORAGE CHURN REDUCTION
Constant downloading and deleting of LLMs destroys flash storage health over time.
- **Policy**: Avoid unnecessary re-downloads. If a model file exists and its checksum is valid, bypass the download phase even if the manifest updates the metadata.
- **Delta Updates**: For IDE patches, transmit only diffs rather than full files.

## MEMORY AND ENERGY EFFICIENCY
Running LLMs on-device generates heat and drains battery.
- **Unload Policy**: Models must be eagerly unloaded from RAM if the user backgrounds the app for > 5 minutes, unless a background Hermes workflow is explicitly active.
- **Small Model Preference**: Default prompts and UI interactions should bias towards `< 3B` parameter models unless deep reasoning is explicitly required. 

## NETWORK USAGE
- Sync polling should only occur on Wi-Fi by default.
- Large model downloads must explicitly prompt the user if they are on a metered connection.
