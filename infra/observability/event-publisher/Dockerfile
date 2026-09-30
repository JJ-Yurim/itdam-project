FROM python:3.12-slim AS base

WORKDIR /app
COPY publish_event.py ./publish_event.py

FROM base AS test
COPY test_publish_event.py ./test_publish_event.py
RUN python -m unittest -v test_publish_event.py
CMD ["python", "-m", "unittest", "-v", "test_publish_event.py"]

FROM base AS production
ENTRYPOINT ["python", "/app/publish_event.py"]
