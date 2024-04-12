# nf-polly plugin

This project contains a Nextflow plugin called `nf-polly` which provides utilities for using the
Polly platform developed by [Elucidata](https://www.elucidata.io/).

## Plugin structure

- `settings.gradle` – Gradle project settings.

- `plugins/nf-polly` – The plugin implementation base directory.

- `plugins/nf-polly/build.gradle` – Plugin Gradle build file. Project dependencies should be added
  here.

- `plugins/nf-polly/src/resources/META-INF/MANIFEST.MF` – Manifest file defining the plugin
  attributes e.g. name, version, etc. The attribute `Plugin-Class` declares the plugin main class.
  This class should extend the base class `nextflow.plugin.BasePlugin`
  e.g. `nextflow.polly.PollyPlugin`.

- `plugins/nf-polly/src/resources/META-INF/extensions.idx` – This file declares one or more
  extension classes provided by the plugin. Each line should contain
  the fully qualified name of a Java class that implements the `org.pf4j.ExtensionPoint` interface (
  or a sub-interface).

- `plugins/nf-polly/src/main` – The plugin implementation sources.

- `plugins/nf-polly/src/test` – The plugin unit tests.

## Plugin classes

- `PollyConfig` – to handle options from the Nextflow configuration

- `PollyExtension` – contains custom utilities (operators, functions, etc.) for use in the Polly
  pipelines environment.

- `PollyPlugin` – the plugin entry point

## Unit testing

To run your unit tests, run the following command in the project root directory (ie. where the file
`settings.gradle` is located):

```bash
./gradlew check
```

## Testing and debugging

To build and test the plugin during development, configure a local Nextflow build with the following
steps:

1. Clone the Nextflow repository in your computer into a sibling directory:
    ```bash
    git clone --depth 1 https://github.com/nextflow-io/nextflow ../nextflow
    ```

2. Configure the plugin build to use the local Nextflow code:
    ```bash
    echo "includeBuild('../nextflow')" >> settings.gradle
    ```

   (Make sure to not add it more than once!)

3. Compile the plugin alongside the Nextflow code:
    ```bash
    make assemble
    ```

4. Run Nextflow with the plugin, using `./launch.sh` as a drop-in replacement for the `nextflow`
   command, and adding the option `-plugins nf-polly` to load the plugin:
    ```bash
    ./launch.sh run my_org/my_pipeline -plugins nf-polly
    ```

## Publishing with a Nextflow installation

This plugin can be made available to a local Nextflow installation using the following steps:

1. Build the plugin: `make buildPlugins`
2. Copy `build/plugins/nf-polly-1.2.3` to `$HOME/.nextflow/plugins`

To test, create a pipeline that uses the plugin and run it: `nextflow run ./my-pipeline-script.nf`
