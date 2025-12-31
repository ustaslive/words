FROM mcr.microsoft.com/devcontainers/base:jammy

ARG CMDLINE_TOOLS_VERSION=11076708
ARG ANDROID_PLATFORM=android-34
ARG ANDROID_BUILD_TOOLS_VERSION=34.0.0
ARG ANDROID_NDK_VERSION=26.1.10909125
ARG GRADLE_VERSION=8.4

ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_SDK_ROOT=/opt/android/sdk
ENV ANDROID_HOME=${ANDROID_SDK_ROOT}
ENV GRADLE_HOME=/opt/gradle/gradle-${GRADLE_VERSION}
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${GRADLE_HOME}/bin:${PATH}

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    openjdk-17-jdk \
    unzip \
    wget \
    curl \
    iputils-ping \
    netcat-openbsd \
    git \
    zip \
    ripgrep \
    build-essential \
    python3 \
    python3-pip \
    gnupg \
    ca-certificates \
    libc6-i386 \
    lib32stdc++6 \
    lib32z1 \
    libbz2-1.0 \
    libncurses5 \
    libgl1 && \
    rm -rf /var/lib/apt/lists/*

RUN curl -fsSL https://deb.nodesource.com/setup_current.x | bash - && \
    apt-get install -y --no-install-recommends nodejs && \
    npm install -g @openai/codex@latest @google/gemini-cli@latest && \
    npm cache clean --force && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    cd /tmp && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip && \
    unzip -q commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip && \
    rm commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip && \
    mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    mv cmdline-tools/* ${ANDROID_SDK_ROOT}/cmdline-tools/latest/ && \
    yes | sdkmanager --licenses && \
    sdkmanager --install \
    "platform-tools" \
    "platforms;${ANDROID_PLATFORM}" \
    "build-tools;${ANDROID_BUILD_TOOLS_VERSION}" \
    "ndk;${ANDROID_NDK_VERSION}" && \
    rm -rf /tmp/* && \
    chown -R vscode:vscode ${ANDROID_SDK_ROOT}

RUN mkdir -p /opt/gradle && \
    cd /tmp && \
    wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip && \
    unzip -q gradle-${GRADLE_VERSION}-bin.zip -d /opt/gradle && \
    rm gradle-${GRADLE_VERSION}-bin.zip && \
    chown -R vscode:vscode /opt/gradle

USER vscode
