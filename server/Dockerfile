FROM python:3.11-slim@sha256:6d98ca198cea726f2c86da2699594339a7b7ff08e49728797b4ed6e3b5c3b62a

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

ENV HOST=0.0.0.0
ENV PORT=9999
ENV WS_PING_INTERVAL_SECONDS=20
ENV WS_PING_TIMEOUT_SECONDS=20

EXPOSE 9999

CMD ["python", "-m", "app.main"]
