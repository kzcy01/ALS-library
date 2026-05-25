FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

# Copy the raw Java source files into the container
COPY . .

# Move directly into the src directory where the file lives
WORKDIR /app/src

# Compile directly on the cloud server using absolute minimum memory tricks
RUN javac -J-Xms64m -J-Xmx128m -encoding UTF-8 AdvancedLibrarySystem.java

# Boot the application directly from the freshly compiled local directory
CMD ["java", "-Xms64m", "-Xmx192m", "AdvancedLibrarySystem"]