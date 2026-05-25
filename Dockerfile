FROM eclipse-temurin:17-jdk
WORKDIR /app

# Copy all project files into the container
COPY . .

# Find the file, restrict compiler memory to 256MB, compile, and run cleanly
CMD ["sh", "-c", "FILE_PATH=$(find . -name 'AdvancedLibrarySystem.java' | head -n 1); if [ -n \"$FILE_PATH\" ]; then echo \"Found file at: $FILE_PATH\"; cp \"$FILE_PATH\" ./AdvancedLibrarySystem.java; javac -J-Xmx256m AdvancedLibrarySystem.java && java -Xmx256m AdvancedLibrarySystem; else echo 'CRITICAL ERROR: AdvancedLibrarySystem.java cannot be found anywhere!'; exit 1; fi"]