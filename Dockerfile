FROM eclipse-temurin:17-jdk
WORKDIR /app

# Copy your project files (including the 'out' directory) into the container
COPY . .

# Run the pre-compiled class file directly from the out folder
CMD ["java", "-cp", "out/production/untitled:out", "AdvancedLibrarySystem"]