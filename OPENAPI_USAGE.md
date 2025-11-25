# OpenAPI usage and visualization

The repository already contains the generated OpenAPI spec at `./openapi.yaml`. Use the steps below to view it locally and to serve it from the running Spring Boot application.

## Visualize locally (no code changes required)

### Swagger UI via Docker (recommended)
1. From the project root, run:
   ```bash
   docker run --rm -p 8081:8080 \
     -e SWAGGER_JSON=/spec/openapi.yaml \
     -v "$(pwd)/openapi.yaml:/spec/openapi.yaml" \
     swaggerapi/swagger-ui
   ```
2. Open http://localhost:8081/ in your browser to browse and try requests.

### ReDoc CLI (Node-based alternative)
1. Install ReDoc CLI if needed: `npm install -g @redocly/cli`.
2. Serve the spec with live preview:
   ```bash
   redocly preview-docs openapi.yaml
   ```
3. Open the printed localhost URL (typically http://localhost:8080) to view the documentation.

## Serve from the Spring Boot app (code change)
Add Swagger UI with SpringDoc so the API docs are available with the application itself.

1. Add the dependency in `pom.xml`:
   ```xml
   <dependency>
     <groupId>org.springdoc</groupId>
     <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
     <version>2.6.0</version>
   </dependency>
   ```
2. Copy `openapi.yaml` into `src/main/resources/static/openapi.yaml` (or update the path in step 3 if you keep it elsewhere).
3. Expose Swagger UI using the static file by creating a config class, e.g.:
   ```java
   @Configuration
   public class OpenApiConfig {
     @Bean
     public GroupedOpenApi externalSpec() {
       return GroupedOpenApi.builder()
           .group("nirvana-api")
           .addOpenApiCustomiser(openApi -> {
             // Static spec will be served by Swagger UI; this bean keeps the UI enabled.
           })
           .build();
     }
   }
   ```
   With the starter, the UI is available at `/swagger-ui/index.html` and the raw spec at `/v3/api-docs`.
4. Start the app normally (`./mvnw spring-boot:run`) and open http://localhost:8080/swagger-ui/index.html.

> If you want the UI to load the static file instead of generated docs, set `springdoc.swagger-ui.url=/openapi.yaml` in `application.yml` so the UI points to your checked-in spec.

## CI validation (optional)
To guard against breaking the spec, add an OpenAPI linter or validator in CI:
```bash
npx @redocly/cli lint openapi.yaml
```
