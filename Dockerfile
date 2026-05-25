FROM eclipse-temurin:17-jdk
WORKDIR /app

# Copy all project files into the container
COPY . .

# This script auto-detects where your Java file is and runs it natively
CMD ["sh", "-c", "if [ -f \"AdvancedLibrarySystem.java\" ]; then javac AdvancedLibrarySystem.java && java AdvancedLibrarySystem; elif [ -f \"src/AdvancedLibrarySystem.java\" ]; then javac src/AdvancedLibrarySystem.java && java -cp src AdvancedLibrarySystem; else echo 'CRITICAL ERROR: AdvancedLibrarySystem.java could not be found anywhere!'; exit 1; fi"]