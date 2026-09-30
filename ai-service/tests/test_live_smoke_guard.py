from scripts import smoke_gemini_evaluation


def test_live_smoke_requires_explicit_opt_in(monkeypatch):
    monkeypatch.delenv("RUN_LIVE_AI_SMOKE", raising=False)
    monkeypatch.setattr(
        smoke_gemini_evaluation,
        "run_smoke",
        lambda _url: (_ for _ in ()).throw(AssertionError("network must not run")),
    )

    assert smoke_gemini_evaluation.main() == 2
