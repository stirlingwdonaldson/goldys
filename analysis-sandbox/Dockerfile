# Pin the resolved base digest in the deployment receipt before activation.
FROM ubuntu:24.04@sha256:73b999393d07f2bf65a40373778a44dc9a6d54ad7718d29247d8ff9413c4d6a8
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    python3 python3-pandas python3-numpy python3-openpyxl python3-xlsxwriter \
    python3-matplotlib python3-pypdf python3-docx python3-reportlab \
    python3-pil tesseract-ocr poppler-utils \
    && rm -rf /var/lib/apt/lists/*
ENV MPLCONFIGDIR=/tmp/matplotlib HOME=/tmp LANG=C.UTF-8 OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1
RUN mkdir /inputs /output && chmod 755 /inputs /output
USER 65532:65532
WORKDIR /output
CMD ["python3", "-I", "-B", "/script.py"]
