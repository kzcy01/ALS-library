FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY . .
RUN javac src/AdvancedLibrarySystem.java
CMD ["java", "-cp", "src", "AdvancedLibrarySystem"]