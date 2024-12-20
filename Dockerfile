FROM ubuntu:24.04

# Install base dependencies
RUN apt-get update && \
    apt-get install -y apt-transport-https ca-certificates gnupg curl openjdk-21-jdk unzip wget

# Install the Google Cloud CLI
RUN curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | gpg --dearmor -o /usr/share/keyrings/cloud.google.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" | tee -a /etc/apt/sources.list.d/google-cloud-sdk.list

RUN apt-get update && \
    apt-get install -y google-cloud-cli

# Install Kotlin
RUN wget https://github.com/JetBrains/kotlin/releases/download/v2.1.0/kotlin-compiler-2.1.0.zip
RUN unzip /kotlin-compiler-2.1.0.zip && rm /kotlin-compiler-2.1.0.zip

ADD scripts /scripts

# Warm up the parser (download dependencies, compile the script, etc)
ENV PATH=/kotlinc/bin:$PATH
RUN /scripts/parser.main.kts || true

ADD config /config

ENTRYPOINT ["/scripts/entrypoint.sh"]
