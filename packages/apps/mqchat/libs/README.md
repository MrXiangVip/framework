# Vendored runtime dependencies

The app build expands these jars into the APK, preserving native SQLite resources and license notices. No network download is needed when building or running the app.

| Artifact | Version | Source | License |
| --- | --- | --- | --- |
| sqlite-jdbc | 3.53.4.0 | [Xerial](https://github.com/xerial/sqlite-jdbc/releases/tag/3.53.4.0), [Maven Central jar](https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.53.4.0/sqlite-jdbc-3.53.4.0.jar) | Apache-2.0 / BSD-2-Clause; SQLite public domain; see embedded notices |
| slf4j-api | 2.0.17 | [SLF4J](https://www.slf4j.org/), [Maven Central jar](https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar) | MIT |
| slf4j-nop | 2.0.17 | [Maven Central jar](https://repo.maven.apache.org/maven2/org/slf4j/slf4j-nop/2.0.17/slf4j-nop-2.0.17.jar) | MIT |

SHA-256:

```text
bcb1f51e36f940867e83342f9efbf5968ac44a6bef4d397bb4af7b17b45cd2fb  sqlite-jdbc-3.53.4.0.jar
7b751d952061954d5abfed7181c1f645d336091b679891591d63329c622eb832  slf4j-api-2.0.17.jar
3716f83649ec66161a2edefd4f49df34d1dd1c51cdcf941996c6987260f0a829  slf4j-nop-2.0.17.jar
```

SQLite JDBC includes native libraries for both Windows and Linux. Java 8 / WSL Ubuntu and the actual APK class loader are covered by the integration tests.
