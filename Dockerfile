FROM eclipse-temurin:17-jdk
WORKDIR /app

# Copy absolutely everything in your project into the container
COPY . .

# Find the file wherever it exists, move it to the root of /app, compile, and run it cleanly
CMD ["sh", "-c", "FILE_PATH=$(find . -name 'AdvancedLibrarySystem.java' | head -n 1); if [ -n \"$FILE_PATH\" ]; then echo \"Found file at: $FILE_PATH\"; cp \"$FILE_PATH\" ./AdvancedLibrarySystem.java; javac AdvancedLibrarySystem.java && java AdvancedLibrarySystem; else echo 'CRITICAL ERROR: AdvancedLibrarySystem.java cannot be found anywhere in this repository!'; exit 1; fi"]