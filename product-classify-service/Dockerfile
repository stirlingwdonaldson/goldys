FROM python:3.11-slim

WORKDIR /app

# sentence-transformers installs CPU-only torch from PyPI via pip, so no separate torch
# wheel index or git tooling is needed (unlike forecast-service, which pins a git+https
# dependency).
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY predictor.py app.py ./

# Model weights are downloaded on first warm-up (first /embed or /cluster) and cached
# here so a restart never re-downloads them (~90 MB).
ENV HF_HOME=/model-cache
VOLUME ["/model-cache"]

EXPOSE 8002

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8002"]
