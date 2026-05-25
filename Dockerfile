FROM eclipse-temurin:21-jdk
WORKDIR /app

# Install wget safely
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

# Download the required MySQL driver archive dependency
RUN wget https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar

# Copy full workspace files
COPY . .

# Compile source with options set BEFORE source files
RUN javac -cp "mysql-connector-j-8.3.0.jar" src/AdvancedLibrarySystem.java

# Run headlessly mapping the driver to the classpath
CMD ["java", "-Djava.awt.headless=true", "-cp", "mysql-connector-j-8.3.0.jar:src", "AdvancedLibrarySystem"]