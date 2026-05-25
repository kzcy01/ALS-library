# Step 1: Download the official MySQL driver dependency
FROM alpine:latest AS builder
WORKDIR /download
RUN apk add --no-cache wget && \
    wget https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar

# Step 2: Use stable Java Runtime to handle the environment
FROM eclipse-temurin:21-jdk
WORKDIR /app

# Copy project files into the directory
COPY . .

# Grab the database connection engine driver from Stage 1
COPY --from=builder /download/mysql-connector-j-8.3.0.jar .

# Compile your java class file directly using classpath referencing
RUN javac -cp "mysql-connector-j-8.3.0.jar" src/AdvancedLibrarySystem.java

# Execute the background worker safely while silencing missing desktop environments
CMD ["java", "-Djava.awt.headless=true", "-cp", "mysql-connector-j-8.3.0.jar:src", "AdvancedLibrarySystem"]