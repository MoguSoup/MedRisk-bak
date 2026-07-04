FROM python:3.12-slim

WORKDIR /app
RUN sed -i 's|http://deb.debian.org/debian|http://mirrors.tencentyun.com/debian|g; s|http://deb.debian.org/debian-security|http://mirrors.tencentyun.com/debian-security|g; s|http://security.debian.org/debian-security|http://mirrors.tencentyun.com/debian-security|g' /etc/apt/sources.list.d/debian.sources \
    && apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch==2.6.0 \
    && pip install --no-cache-dir -i https://mirrors.tencent.com/pypi/simple/ --trusted-host mirrors.tencent.com -r requirements.txt
COPY app ./app

EXPOSE 8090
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090"]
