FROM openjdk:21-slim
WORKDIR /app

# Copy all local project files into the container
COPY . .

# Check where the compiled class file sits, handle Java versioning, and run instantly
CMD ["sh", "-c", "if [ -d \"out/production/untitled\" ]; then java -cp out/production/untitled:out AdvancedLibrarySystem; else java -cp src AdvancedLibrarySystem; fi"]