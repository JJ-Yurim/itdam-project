from fastapi.testclient import TestClient

from app.application import create_app
from app.config import Settings


def test_health_is_available_without_llm_credentials():
    settings = Settings(
        _env_file=None,
        app_name="mvp-ai-service-test",
        app_env="test",
        llm_provider=None,
        llm_api_key=None,
        llm_model=None,
        llm_base_url=None,
    )

    with TestClient(create_app(settings)) as client:
        response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"
    assert response.json()["service"] == "mvp-ai-service-test"
    assert response.json()["llmConfigured"] is False
