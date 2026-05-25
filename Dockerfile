FROM eclipse-temurin:17-jdk
WORKDIR /app

# Copy your local project files (including the newly pushed 'out' directory)
COPY . .

# Run the pre-compiled class file directly from the production folder path
CMD ["java", "-cp", "out/production/untitled:out", "AdvancedLibrarySystem"]