FROM alpine:3.21.0

# Install base dependencies
RUN apk add --update python3 curl bash unzip gzip wget openjdk21

# Install the Google Cloud CLI
RUN curl -sSL https://sdk.cloud.google.com > gcloud-sdk-installer && \
    bash gcloud-sdk-installer --install-dir=/ --disable-prompts && \
    rm gcloud-sdk-installer

# Install Kotlin
RUN wget https://github.com/JetBrains/kotlin/releases/download/v2.1.0/kotlin-compiler-2.1.0.zip && \
    unzip /kotlin-compiler-2.1.0.zip && \
    rm /kotlin-compiler-2.1.0.zip

ADD scripts /scripts

# Warm up the parser (download dependencies, compile the script, etc)
RUN /kotlinc/bin/kotlin scripts/parser.main.kts || true

ADD config /config

ENV PATH=/google-cloud-sdk/bin:/kotlinc/bin:$PATH
ENTRYPOINT ["/scripts/entrypoint.sh"]
