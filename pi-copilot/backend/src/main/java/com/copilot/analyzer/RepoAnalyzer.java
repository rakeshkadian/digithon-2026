package com.copilot.analyzer;

import com.copilot.model.*;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Walks the cloned repo, parses every Java file under the base package and
 * sorts classes into entities, repositories, controllers, services and DTOs.
 * Then builds a "table.column -> code references" map.
 */
@Slf4j
@Service
public class RepoAnalyzer {

    private static final Set<String> SKIP_DIRS = Set.of("test", "target", "build", "node_modules", ".git");
    private final JavaParser parser;

    public RepoAnalyzer() {
        ParserConfiguration cfg = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        this.parser = new JavaParser(cfg);
    }

    // ------------------------------------------------------------------ entry point

    public RepoSnapshot analyze(Path repoDir, String repoUrl, String branch, String commitId) throws IOException {
        RepoSnapshot.RepoSnapshotBuilder snap = RepoSnapshot.builder()
                .repoUrl(repoUrl).branch(branch).commitId(commitId);

        readBuildFile(repoDir, snap);
        readDatasourceConfig(repoDir, snap);

        List<ParsedFile> files = parseAllJava(repoDir, snap);

        String basePackage = detectBasePackage(files);
        snap.basePackage(basePackage);

        List<EntityInfo> entities = new ArrayList<>();
        List<ClassInfo> repos = new ArrayList<>(), controllers = new ArrayList<>(),
                services = new ArrayList<>(), dtos = new ArrayList<>();

        for (ParsedFile pf : files) {
            for (ClassOrInterfaceDeclaration c : pf.cu().findAll(ClassOrInterfaceDeclaration.class)) {
                if (c.isAnnotationPresent("Entity")) {
                    entities.add(readEntity(c, pf.relPath()));
                } else if (extendsAny(c, "JpaRepository", "CrudRepository", "PagingAndSortingRepository")) {
                    repos.add(readRepository(c, pf.relPath()));
                } else if (c.isAnnotationPresent("RestController") || c.isAnnotationPresent("Controller")) {
                    controllers.add(readClass(c, pf.relPath(), "CONTROLLER"));
                } else if (c.isAnnotationPresent("Service") || c.isAnnotationPresent("Component")) {
                    services.add(readClass(c, pf.relPath(), "SERVICE"));
                }
            }
        }
        // DTOs: any remaining plain class whose field names overlap an entity's fields
        Set<String> entityFieldNames = new HashSet<>();
        entities.forEach(e -> e.getFields().forEach(f -> entityFieldNames.add(f.getJavaName())));
        for (ParsedFile pf : files) {
            for (ClassOrInterfaceDeclaration c : pf.cu().findAll(ClassOrInterfaceDeclaration.class)) {
                if (isAlreadyBucketed(c) || c.isInterface()) continue;
                List<String> names = c.getFields().stream()
                        .flatMap(f -> f.getVariables().stream()).map(v -> v.getNameAsString()).toList();
                if (names.stream().filter(entityFieldNames::contains).count() >= 2) {
                    ClassInfo dto = readClass(c, pf.relPath(), "DTO");
                    dto.setFieldNames(names);
                    dtos.add(dto);
                }
            }
        }

        snap.entities(entities).repositories(repos).controllers(controllers).services(services).dtos(dtos);
        snap.codeRefs(buildCodeRefs(entities, repos, controllers, services, dtos, files));

        RepoSnapshot result = snap.build();
        log.info("Analyzed repo: {} entities, {} repositories, {} controllers, {} services, {} dtos",
                entities.size(), repos.size(), controllers.size(), services.size(), dtos.size());
        return result;
    }

    // ------------------------------------------------------------------ build + config files

    private void readBuildFile(Path repoDir, RepoSnapshot.RepoSnapshotBuilder snap) throws IOException {
        Path pom = repoDir.resolve("pom.xml");
        if (Files.exists(pom)) {
            String xml = Files.readString(pom);
            Matcher m = Pattern.compile("<java.version>(.*?)</java.version>").matcher(xml);
            if (m.find()) snap.javaVersion(m.group(1).trim());
        }
    }

    /** Reads spring.datasource.url / username from application.yml or .properties. Password is never stored. */
    private void readDatasourceConfig(Path repoDir, RepoSnapshot.RepoSnapshotBuilder snap) throws IOException {
        Path res = repoDir.resolve("src/main/resources");
        if (!Files.isDirectory(res)) return;

        for (String name : List.of("application.yml", "application.yaml")) {
            Path p = res.resolve(name);
            if (Files.exists(p)) {
                JsonNode root = new YAMLMapper().readTree(p.toFile());
                JsonNode ds = root.path("spring").path("datasource");
                if (!ds.isMissingNode()) {
                    snap.datasourceUrl(ds.path("url").asText(null));
                    snap.datasourceUser(ds.path("username").asText(null));
                    snap.datasourcePasswordPresent(ds.hasNonNull("password"));
                    return;
                }
            }
        }
        Path props = res.resolve("application.properties");
        if (Files.exists(props)) {
            Properties pr = new Properties();
            try (var in = Files.newInputStream(props)) { pr.load(in); }
            snap.datasourceUrl(pr.getProperty("spring.datasource.url"));
            snap.datasourceUser(pr.getProperty("spring.datasource.username"));
            snap.datasourcePasswordPresent(pr.getProperty("spring.datasource.password") != null);
        }
    }

    // ------------------------------------------------------------------ parsing

    private record ParsedFile(String relPath, CompilationUnit cu) {}

    private List<ParsedFile> parseAllJava(Path repoDir, RepoSnapshot.RepoSnapshotBuilder snap) throws IOException {
        List<ParsedFile> out = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(repoDir)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !inSkippedDir(repoDir.relativize(p)))
                .forEach(p -> {
                    String rel = repoDir.relativize(p).toString().replace('\\', '/');
                    try {
                        var res = parser.parse(p);
                        if (res.isSuccessful() && res.getResult().isPresent()) {
                            out.add(new ParsedFile(rel, res.getResult().get()));
                        } else {
                            skipped.add(rel);
                        }
                    } catch (IOException e) {
                        skipped.add(rel);
                    }
                });
        }
        snap.skippedFiles(skipped);
        return out;
    }

    private boolean inSkippedDir(Path rel) {
        for (Path part : rel) if (SKIP_DIRS.contains(part.toString())) return true;
        return false;
    }

    private String detectBasePackage(List<ParsedFile> files) {
        return files.stream()
                .filter(pf -> pf.cu().findAll(ClassOrInterfaceDeclaration.class).stream()
                        .anyMatch(c -> c.isAnnotationPresent("SpringBootApplication")))
                .map(pf -> pf.cu().getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse(""))
                .findFirst().orElse("");
    }

    // ------------------------------------------------------------------ entity

    private EntityInfo readEntity(ClassOrInterfaceDeclaration c, String path) {
        String table = c.getAnnotationByName("Table")
                .flatMap(a -> memberValue(a, "name"))
                .orElse(toSnake(c.getNameAsString()));

        List<FieldInfo> fields = new ArrayList<>();
        for (FieldDeclaration f : c.getFields()) {
            if (f.isStatic() || f.isAnnotationPresent("Transient")) continue;
            // collections / relations are not columns of this table
            if (f.isAnnotationPresent("OneToMany") || f.isAnnotationPresent("ManyToMany")) continue;

            f.getVariables().forEach(v -> {
                Optional<AnnotationExpr> col = f.getAnnotationByName("Column");
                String colName = col.flatMap(a -> memberValue(a, "name")).orElse(toSnake(v.getNameAsString()));
                Integer length = col.flatMap(a -> memberValue(a, "length")).map(Integer::valueOf).orElse(null);
                boolean nullable = col.flatMap(a -> memberValue(a, "nullable")).map(Boolean::valueOf).orElse(true);
                fields.add(FieldInfo.builder()
                        .javaName(v.getNameAsString())
                        .columnName(colName)
                        .javaType(f.getElementType().asString())
                        .id(f.isAnnotationPresent("Id"))
                        .nullable(nullable)
                        .length(length)
                        .build());
            });
        }
        return EntityInfo.builder().className(c.getNameAsString()).filePath(path).tableName(table).fields(fields).build();
    }

    // ------------------------------------------------------------------ repository / other classes

    private ClassInfo readRepository(ClassOrInterfaceDeclaration c, String path) {
        ClassInfo info = readClass(c, path, "REPOSITORY");
        c.getExtendedTypes().stream()
                .filter(t -> t.getNameAsString().endsWith("Repository"))
                .findFirst()
                .flatMap(ClassOrInterfaceType::getTypeArguments)
                .filter(args -> !args.isEmpty())
                .ifPresent(args -> info.setManagedEntity(args.get(0).asString()));
        return info;
    }

    private ClassInfo readClass(ClassOrInterfaceDeclaration c, String path, String kind) {
        List<String> methods = new ArrayList<>();
        List<String> queries = new ArrayList<>();
        for (MethodDeclaration m : c.getMethods()) {
            methods.add(m.getNameAsString());
            m.getAnnotationByName("Query").flatMap(a -> memberValue(a, "value")).ifPresent(queries::add);
        }
        return ClassInfo.builder().className(c.getNameAsString()).filePath(path).kind(kind)
                .methodNames(methods).queries(queries).build();
    }

    private boolean isAlreadyBucketed(ClassOrInterfaceDeclaration c) {
        return c.isAnnotationPresent("Entity") || c.isAnnotationPresent("RestController")
                || c.isAnnotationPresent("Controller") || c.isAnnotationPresent("Service")
                || c.isAnnotationPresent("Component") || c.isAnnotationPresent("Configuration")
                || c.isAnnotationPresent("SpringBootApplication")
                || extendsAny(c, "JpaRepository", "CrudRepository", "PagingAndSortingRepository");
    }

    // ------------------------------------------------------------------ code references

    private Map<String, List<CodeRef>> buildCodeRefs(List<EntityInfo> entities, List<ClassInfo> repos,
                                                     List<ClassInfo> controllers, List<ClassInfo> services,
                                                     List<ClassInfo> dtos, List<ParsedFile> files) {
        Map<String, List<CodeRef>> refs = new LinkedHashMap<>();

        for (EntityInfo e : entities) {
            for (FieldInfo f : e.getFields()) {
                String key = e.getTableName() + "." + f.getColumnName();
                List<CodeRef> list = new ArrayList<>();
                String field = f.getJavaName();
                String cap = Character.toUpperCase(field.charAt(0)) + field.substring(1);

                list.add(new CodeRef(e.getFilePath(), e.getClassName(), field, 0, "ENTITY_FIELD"));

                // repository derived queries and @Query strings
                for (ClassInfo r : repos) {
                    if (r.getManagedEntity() != null && !r.getManagedEntity().equals(e.getClassName())) continue;
                    r.getMethodNames().stream().filter(m -> m.contains("By" + cap) || m.contains(cap))
                            .forEach(m -> list.add(new CodeRef(r.getFilePath(), r.getClassName(), m, 0, "REPO_METHOD")));
                    r.getQueries().stream().filter(q -> q.contains(field) || q.contains(f.getColumnName()))
                            .forEach(q -> list.add(new CodeRef(r.getFilePath(), r.getClassName(), "@Query", 0, "QUERY")));
                }
                // DTO fields with the same name
                for (ClassInfo d : dtos) {
                    if (d.getFieldNames().contains(field))
                        list.add(new CodeRef(d.getFilePath(), d.getClassName(), field, 0, "DTO_FIELD"));
                }
                // getX()/setX() calls in services and controllers
                Set<String> svcFiles = new HashSet<>();
                services.forEach(s -> svcFiles.add(s.getFilePath()));
                controllers.forEach(s -> svcFiles.add(s.getFilePath()));
                for (ParsedFile pf : files) {
                    if (!svcFiles.contains(pf.relPath())) continue;
                    pf.cu().findAll(MethodCallExpr.class).stream()
                        .filter(mc -> mc.getNameAsString().equals("get" + cap) || mc.getNameAsString().equals("set" + cap))
                        .forEach(mc -> {
                            String enclosing = mc.findAncestor(MethodDeclaration.class)
                                    .map(MethodDeclaration::getNameAsString).orElse("?");
                            String cls = mc.findAncestor(ClassOrInterfaceDeclaration.class)
                                    .map(ClassOrInterfaceDeclaration::getNameAsString).orElse("?");
                            int line = mc.getBegin().map(b -> b.line).orElse(0);
                            list.add(new CodeRef(pf.relPath(), cls, enclosing, line, "METHOD_CALL"));
                        });
                }
                refs.put(key, list);
            }
        }
        return refs;
    }

    // ------------------------------------------------------------------ helpers

    private static boolean extendsAny(ClassOrInterfaceDeclaration c, String... names) {
        Set<String> wanted = Set.of(names);
        return c.getExtendedTypes().stream().anyMatch(t -> wanted.contains(t.getNameAsString()));
    }

    /** Returns the plain value of an annotation member, e.g. @Column(name = "first_name") -> first_name */
    private static Optional<String> memberValue(AnnotationExpr a, String member) {
        if (a instanceof NormalAnnotationExpr n) {
            return n.getPairs().stream()
                    .filter(p -> p.getNameAsString().equals(member))
                    .map(p -> literal(p.getValue()))
                    .findFirst();
        }
        if (a instanceof SingleMemberAnnotationExpr s && member.equals("value")) {
            return Optional.of(literal(s.getMemberValue()));
        }
        return Optional.empty();
    }

    private static String literal(Expression e) {
        if (e instanceof StringLiteralExpr s) return s.asString();
        if (e instanceof IntegerLiteralExpr i) return i.getValue();
        if (e instanceof BooleanLiteralExpr b) return String.valueOf(b.getValue());
        return e.toString();
    }

    /** Spring Boot's default naming strategy: firstName -> first_name, Employee -> employee */
    static String toSnake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
