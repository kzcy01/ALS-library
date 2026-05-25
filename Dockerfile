FROM eclipse-temurin:17-jdk
WORKDIR /app
# Copy everything into the container
COPY . .
# Change our active location directly INTO the src folder
WORKDIR /app/src
# Compile and run natively right where the file sits
RUN javac AdvancedLibrarySystem.java
CMD ["java", "AdvancedLibrarySystem"]