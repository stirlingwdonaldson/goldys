FROM python:3.11-slim

WORKDIR /app

# Install CPU-only torch first, from PyTorch's CPU wheel index, so the multi-GB CUDA
# build is never pulled. Installing it before `requirements.txt` means the `torch` line
# there resolves to the already-present CPU wheel instead of downloading from PyPI.
COPY requirements.txt .
RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir -r requirements.txt

COPY predictor.py app.py ./

# Model weights are downloaded on first warm-up (first /forecast) and cached here so a
# restart never re-downloads them (~hundreds of MB).
ENV TIMESFM_CACHE_DIR=/timesfm-cache
VOLUME ["/timesfm-cache"]

EXPOSE 8001

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8001"]
