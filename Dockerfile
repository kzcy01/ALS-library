FROM eclipse-temurin:21-jdk
WORKDIR /workspace

# 1. Copy the entire repository into the container workspace
COPY . .

# 2. Force delete any old, half-compiled, or corrupted class files from Git tracking
RUN find . -name "*.class" -delete && rm -rf out/ target/ build/

# 3. Dynamically find the raw Java code file, bring it to the root, and compile it fresh
RUN FILE_PATH=$(find . -name "AdvancedLibrarySystem.java" | head -n 1) && \
    cp "$FILE_PATH" ./AdvancedLibrarySystem.java && \
    javac -encoding UTF-8 AdvancedLibrarySystem.java

# 4. Boot the application with Headless Mode activated
CMD ["java", "-Djava.awt.headless=true", "-Xms64m", "-Xmx256m", "AdvancedLibrarySystem"]