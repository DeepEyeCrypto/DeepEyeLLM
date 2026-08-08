pub mod memory;
pub mod router;
pub mod models;
pub mod inference;

use models::ModelRegistry;
use inference::{InferenceEngine, LiteRTEngine};
use std::sync::Mutex;
use std::sync::OnceLock;

// Generate the UniFFI scaffolding
uniffi::include_scaffolding!("api");

static REGISTRY: OnceLock<ModelRegistry> = OnceLock::new();
static ENGINE: OnceLock<Mutex<LiteRTEngine>> = OnceLock::new();

pub fn initialize_engine() {
    REGISTRY.get_or_init(|| ModelRegistry::new());
    ENGINE.get_or_init(|| {
        let mut engine = LiteRTEngine::new();
        if let Some(registry) = REGISTRY.get() {
            if let Some(config) = registry.get_model("gemma-4-2b-it") {
                let _ = engine.load_model(config);
            }
        }
        Mutex::new(engine)
    });
    println!("DeepEye Core: Engine initialized with Gemma 4 (2B) default.");
}

pub fn get_available_models() -> Vec<String> {
    if let Some(registry) = REGISTRY.get() {
        registry.list_models().into_iter().map(|m| m.id).collect()
    } else {
        vec!["gemma-4-2b-it".to_string(), "gemma-4-4b-it".to_string()]
    }
}

pub fn route_query(prompt: String) -> router::RouteDecision {
    router::TaskRouter::route_task(&prompt)
}

pub fn load_model(model_id: String) -> String {
    if let Some(registry) = REGISTRY.get() {
        if let Some(config) = registry.get_model(&model_id) {
            if let Some(engine_lock) = ENGINE.get() {
                let mut engine = engine_lock.lock().unwrap();
                match engine.load_model(config) {
                    Ok(_) => return format!("Model loaded successfully: {}", model_id),
                    Err(e) => return format!("Failed to load model: {}", e),
                }
            }
        }
    }
    format!("Error: Engine not initialized or model not found.")
}

pub fn unload_model() {
    if let Some(engine_lock) = ENGINE.get() {
        let mut engine = engine_lock.lock().unwrap();
        let _ = engine.unload();
    }
}

pub fn run_inference(prompt: String) -> String {
    if let Some(engine_lock) = ENGINE.get() {
        let engine = engine_lock.lock().unwrap();
        match engine.generate(&prompt) {
            Ok(output) => output,
            Err(e) => format!("Inference Error: {}", e),
        }
    } else {
        "Error: Engine not initialized.".to_string()
    }
}

