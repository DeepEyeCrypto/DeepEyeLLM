use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RouteDecision {
    pub target: String, // "local" | "cloud"
    pub engine: String, // "Gemma 4 (2B) Local" | "Hermes Cloud Fallback"
    pub reason: String,
    pub latency_est_ms: u32,
}

pub struct TaskRouter {}

impl TaskRouter {
    pub fn route_task(task: &str) -> RouteDecision {
        let task_lower = task.to_lowercase();
        
        // Analyze query complexity based on length, keywords, and semantic hints
        let is_complex = task_lower.contains("heavy") 
            || task_lower.contains("deep debug") 
            || task_lower.contains("cloud")
            || task.len() > 250
            || task_lower.contains("analyze architecture");

        if is_complex {
            RouteDecision {
                target: "cloud".to_string(),
                engine: "Hermes Cloud Fallback".to_string(),
                reason: "Complex task / explicit cloud fallback requested".to_string(),
                latency_est_ms: 120,
            }
        } else {
            RouteDecision {
                target: "local".to_string(),
                engine: "Gemma 4 (2B) Local".to_string(),
                reason: "Privacy-first on-device execution with local context".to_string(),
                latency_est_ms: 2,
            }
        }
    }
}

