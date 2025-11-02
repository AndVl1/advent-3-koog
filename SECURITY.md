# Chatter Backend

## Setting up API Keys

### For Local Development

1. Copy the example gradle properties file:
   ```bash
   cp server/gradle.properties.example server/gradle.properties
   ```

2. Edit `server/gradle.properties` and add your API keys:
   ```properties
   GOOGLE_API_KEY=your-google-api-key-here
   OPENROUTER_API_KEY=your-openrouter-api-key-here
   ```

### For Production

Set environment variables:

```bash
export GOOGLE_API_KEY=your-google-api-key-here
export OPENROUTER_API_KEY=your-openrouter-api-key-here
```

Or use Docker/Kubernetes secrets or your cloud provider's secret management system.

## Running the Application

### Development

```bash
./gradlew :server:run
```

### Production

```bash
export GOOGLE_API_KEY=your-key
export OPENROUTER_API_KEY=your-key
./gradlew :server:shadowJar
java -jar server/build/libs/server-all.jar
```

## Security Notes

- Never commit API keys to version control
- Use different keys for development and production
- Rotate keys regularly
- Use environment variables or secret management in production