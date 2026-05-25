FROM eclipse-temurin:21-jdk
WORKDIR /app

# Install wget to download the driver securely
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

# Pull the official MySQL JDBC driver
RUN wget https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar

# Copy source repository contents
COPY . .

# Compile code with classpath mapping and preview support enabled
RUN javac --enable-preview --release 21 -cp "mysql-connector-j-8.3.0.jar" src/AdvancedLibrarySystem.java

# Execute headlessly in cloud console mode
CMD ["java", "--enable-preview", "-Djava.awt.headless=true", "-cp", "mysql-connector-j-8.3.0.jar:src", "AdvancedLibrarySystem"]