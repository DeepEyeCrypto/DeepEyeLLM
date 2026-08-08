use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModelConfig {
    pub id: String,
    pub name: String,
    pub family: String,
    pub parameters: String,
    pub context_length: u32,
    pub quantization: String,
}

pub struct ModelRegistry {
    models: HashMap<String, ModelConfig>,
}

impl ModelRegistry {
    pub fn new() -> Self {
        let mut registry = Self {
            models: HashMap::new(),
        };
        registry.load_defaults();
        registry
    }

    fn load_defaults(&mut self) {
        self.models.insert(
            "gemma-4-2b-it".to_string(),
            ModelConfig {
                id: "gemma-4-2b-it".to_string(),
                name: "Gemma 4 (2B) Instruct".to_string(),
                family: "Gemma".to_string(),
                parameters: "2B".to_string(),
                context_length: 8192,
                quantization: "int8".to_string(),
            },
        );
        self.models.insert(
            "gemma-4-4b-it".to_string(),
            ModelConfig {
                id: "gemma-4-4b-it".to_string(),
                name: "Gemma 4 (4B) Instruct".to_string(),
                family: "Gemma".to_string(),
                parameters: "4B".to_string(),
                context_length: 8192,
                quantization: "int4".to_string(),
            },
        );
    }

    pub fn get_model(&self, id: &str) -> Option<ModelConfig> {
        self.models.get(id).cloned()
    }

    pub fn list_models(&self) -> Vec<ModelConfig> {
        self.models.values().cloned().collect()
    }
}
