# Step 1: Download the official MySQL Database driver dependency
FROM alpine:latest AS builder
WORKDIR /download
RUN apk add --no-cache wget && \
    wget https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar

# Step 2: Build the runtime container with OpenJDK 21
FROM eclipse-temurin:21-jdk
WORKDIR /app

# Copy your source code into the container
COPY . .

# Copy the database driver downloaded in Step 1
COPY --from=builder /download/mysql-connector-j-8.3.0.jar .

# Compile your java class file safely inside the container environment
RUN javac src/AdvancedLibrarySystem.java

# Run the web server using Headless mode and include the MySQL driver in the Classpath (-cp)
CMD ["java", "-Djava.awt.headless=true", "-cp", "mysql-connector-j-8.3.0.jar:src", "AdvancedLibrarySystem"]