FROM eclipse-temurin:21-jdk
WORKDIR /app

# Install curl/wget to safely pull dependencies inside the base image
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

# Download the exact JDBC MySQL connector jar to our application root directory
RUN wget https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar

# Copy all project source directories into the image filesystems
COPY . .

# Compile the source class explicitly using our local dependency classpath container reference
RUN javac -cp "mysql-connector-j-8.3.0.jar" src/AdvancedLibrarySystem.java

# Spin up the compiled application binary with appropriate network classpath variables enabled
CMD ["java", "-Djava.awt.headless=true", "-cp", "mysql-connector-j-8.3.0.jar:src", "AdvancedLibrarySystem"]