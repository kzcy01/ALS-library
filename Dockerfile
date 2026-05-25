FROM eclipse-temurin:21-jdk
WORKDIR /app

# Install wget tool dependency
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

# Download the official MySQL JDBC connection driver jar archive dependency
RUN wget https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar

# Copy full repository workspace directory contents into the working image
COPY . .

# Compile source safely targeting Java 21 platform baseline specifications
RUN javac -release 21 -cp "mysql-connector-j-8.3.0.jar" src/AdvancedLibrarySystem.java

# Spin up headlessly matching cloud container environment classpath variables
CMD ["java", "-Djava.awt.headless=true", "-cp", "mysql-connector-j-8.3.0.jar:src", "AdvancedLibrarySystem"]