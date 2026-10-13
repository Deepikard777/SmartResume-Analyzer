package com.resumeanalyzer.service.analysis;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Curated skill dictionary for the deterministic ATS matching engine.
 *
 * Each entry is a canonical skill name (display form) plus optional aliases.
 * Matching is done case-insensitively using whole-word token boundaries.
 *
 * This dictionary is intentionally limited to common software/IT skills for
 * this project version. It is easy to extend by adding entries to the static map.
 *
 * NOTE: This is NOT an exhaustive industry skill database. It is a
 * project-defined curated list used for deterministic scoring.
 */
@Component
public class SkillDictionary {

    /**
     * Maps canonical skill name (lowercase) → set of aliases (also lowercase).
     * The key is the display name used in results. Values are alternate forms
     * that should resolve to the same canonical skill.
     */
    private final Map<String, Set<String>> canonicalToAliases;

    /**
     * Maps each alias (lowercase) → canonical form (lowercase, as stored in key above).
     */
    private final Map<String, String> aliasToCanonical;

    /** Display names keyed by canonical (lowercase) form. */
    private final Map<String, String> canonicalToDisplay;

    public SkillDictionary() {
        canonicalToAliases = new LinkedHashMap<>();
        aliasToCanonical   = new HashMap<>();
        canonicalToDisplay = new LinkedHashMap<>();

        // ---- Programming Languages ----
        register("Java");
        register("Python");
        register("C", "c language");
        register("C++", "cpp", "cplusplus");
        register("C#", "csharp", "c sharp");
        register("JavaScript", "js");
        register("TypeScript", "ts");
        register("Kotlin");
        register("Swift");
        register("Go", "golang");
        register("Rust");
        register("PHP");
        register("Ruby");
        register("Scala");
        register("R");
        register("MATLAB");

        // ---- Backend Frameworks ----
        register("Spring Boot", "springboot");
        register("Spring", "spring framework");
        register("Node.js", "nodejs", "node");
        register("Express", "express.js", "expressjs");
        register("FastAPI");
        register("Django");
        register("Flask");
        register("Laravel");
        register("ASP.NET", "asp.net core", "dotnet");
        register("REST APIs", "rest api", "restful api", "restful apis", "rest");
        register("Microservices", "microservice");
        register("GraphQL");
        register("gRPC");

        // ---- Frontend ----
        register("React", "react.js", "reactjs");
        register("Angular", "angularjs");
        register("Vue", "vue.js", "vuejs");
        register("HTML", "html5");
        register("CSS", "css3");
        register("Tailwind CSS", "tailwind");
        register("Bootstrap");
        register("Redux");
        register("Next.js", "nextjs");

        // ---- Databases ----
        register("MySQL");
        register("PostgreSQL", "postgres");
        register("MongoDB");
        register("SQL");
        register("Oracle", "oracle db");
        register("SQLite");
        register("Redis");
        register("Cassandra");
        register("Elasticsearch");
        register("DynamoDB");

        // ---- DevOps / Cloud ----
        register("Docker");
        register("Kubernetes", "k8s");
        register("AWS", "amazon web services");
        register("Azure", "microsoft azure");
        register("GCP", "google cloud", "google cloud platform");
        register("CI/CD", "cicd");
        register("GitHub Actions", "github action");
        register("Jenkins");
        register("Terraform");
        register("Ansible");
        register("Helm");

        // ---- Tools ----
        register("Git");
        register("GitHub");
        register("GitLab");
        register("Maven");
        register("Gradle");
        register("Linux");
        register("Bash", "shell scripting");
        register("Postman");

        // ---- Data / AI ----
        register("Machine Learning", "ml");
        register("Deep Learning");
        register("TensorFlow", "tensorflow");
        register("PyTorch");
        register("Scikit-learn", "sklearn");
        register("Pandas");
        register("NumPy", "numpy");
        register("Spark", "apache spark");

        // ---- Testing ----
        register("JUnit", "junit5", "junit 5");
        register("Mockito");
        register("Selenium");
        register("Cypress");
        register("Jest");
        register("Pytest");
    }

    /**
     * Registers a skill with its canonical form and optional aliases.
     * The first argument is used as the display name.
     */
    private void register(String displayName, String... aliases) {
        String canonical = displayName.toLowerCase();
        Set<String> aliasSet = new LinkedHashSet<>();
        aliasSet.add(canonical); // canonical is always an alias of itself
        for (String alias : aliases) {
            aliasSet.add(alias.toLowerCase());
        }
        canonicalToAliases.put(canonical, aliasSet);
        canonicalToDisplay.put(canonical, displayName);
        for (String alias : aliasSet) {
            aliasToCanonical.put(alias, canonical);
        }
    }

    /** Returns all canonical skill names (lowercase). */
    public Set<String> getAllCanonicals() {
        return Collections.unmodifiableSet(canonicalToAliases.keySet());
    }

    /** Returns all aliases for a canonical skill. */
    public Set<String> getAliasesFor(String canonical) {
        return canonicalToAliases.getOrDefault(canonical.toLowerCase(), Set.of());
    }

    /**
     * Resolves an alias or canonical form to the canonical (lowercase) form.
     * Returns null if not found.
     */
    public String resolveToCanonical(String term) {
        return aliasToCanonical.get(term.toLowerCase().trim());
    }

    /** Returns display name (original case) for a canonical. */
    public String getDisplayName(String canonical) {
        return canonicalToDisplay.getOrDefault(canonical.toLowerCase(), canonical);
    }
}
