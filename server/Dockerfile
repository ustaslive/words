FROM python:3.11-slim

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
