FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY . .
RUN javac AdvancedLibrarySystem.java
CMD ["java", "AdvancedLibrarySystem"]