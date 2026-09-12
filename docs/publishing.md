# Publishing OpenCleanup

OpenCleanup is published to Maven Central. The current release is available at
[io.github.lhozdroid:opencleanup-maven-plugin:1.1.0](https://central.sonatype.com/artifact/io.github.lhozdroid/opencleanup-maven-plugin/1.1.0):

```text
io.github.lhozdroid:opencleanup-maven-plugin:<version>
```

The `release` Maven profile creates the source and Javadoc JARs, signs the
release with GPG, generates Central checksums, and publishes through the
Central Publisher Portal.

## One-time Central setup

1. Sign in to [Maven Central](https://central.sonatype.com) with the GitHub
   account that owns `lhozdroid`.
2. Verify the `io.github.lhozdroid` namespace.
3. Generate a Central user token.
4. Create a GPG key and publish its public key to a public keyserver accepted
   by Central.

The publishing workflow expects these GitHub repository secrets:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central user-token username |
| `MAVEN_CENTRAL_TOKEN` | Central user-token password |
| `MAVEN_GPG_PRIVATE_KEY` | ASCII-armored GPG private key |
| `MAVEN_GPG_PASSPHRASE` | GPG private-key passphrase |

The private key and passphrase must only be stored as GitHub Actions secrets.

## Creating a signing key

Create a dedicated release key locally, then export the armored private key:

```bash
gpg --full-generate-key
gpg --list-secret-keys --keyid-format LONG
gpg --armor --export-secret-keys YOUR_KEY_ID
```

Copy the command output into the `MAVEN_GPG_PRIVATE_KEY` secret. Publish the
matching public key to a supported keyserver before the first deployment.

## Releasing

Update the project version in `pom.xml`, commit it, and create a matching Git
tag/release. Published Central components are immutable, so a correction must
use a new version.

Publishing starts automatically when a GitHub release is published. It can
also be started manually from the Actions tab after the Central and GitHub
secrets setup is complete.

For a local, signed release, configure the `central` server in
`~/.m2/settings.xml`, import the release GPG key, and run:

```bash
mvn --batch-mode --activate-profiles release clean deploy
```

## Consumer usage

Consumers should declare the published plugin in the project's build section:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.github.lhozdroid</groupId>
      <artifactId>opencleanup-maven-plugin</artifactId>
      <version>1.1.0</version>
      <executions>
        <execution>
          <id>opencleanup-rewrite</id>
          <goals>
            <goal>rewrite</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Maven resolves the plugin from Maven Central automatically, so consumers do
not need a `<repositories>` entry, a repository checkout, or `mvn install`.
The complete rule configuration and lifecycle execution example are in the
[Maven configuration guide](configuration.md).
