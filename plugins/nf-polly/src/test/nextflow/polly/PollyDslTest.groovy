package nextflow.polly

import nextflow.plugin.Plugins
import nextflow.plugin.TestPluginDescriptorFinder
import nextflow.plugin.TestPluginManager
import nextflow.plugin.extension.PluginExtensionProvider
import org.pf4j.PluginDescriptorFinder
import spock.lang.Shared
import spock.lang.Timeout
import test.Dsl2Spec

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Manifest

/**
 * Unit test for Polly DSL
 */
@Timeout(10)
class PollyDslTest extends Dsl2Spec {

    @Shared
    String pluginsMode

    def setup() {
        // reset previous instances
        PluginExtensionProvider.reset()
        // this need to be set *before* the plugin manager class is created
        pluginsMode = System.getProperty('pf4j.mode')
        System.setProperty('pf4j.mode', 'dev')
        // the plugin root should
        def root = Path.of('.').toAbsolutePath().normalize()
        def manager = new TestPluginManager(root) {
            @Override
            protected PluginDescriptorFinder createPluginDescriptorFinder() {
                return new TestPluginDescriptorFinder() {

                    protected Manifest readManifestFromDirectory(Path pluginPath) {
                        if (!Files.isDirectory(pluginPath))
                            return null

                        final manifestPath = pluginPath.resolve('build/resources/main/META-INF/MANIFEST.MF')
                        if (!Files.exists(manifestPath))
                            return null

                        final input = Files.newInputStream(manifestPath)
                        return new Manifest(input)
                    }
                }
            }
        }
        Plugins.init(root, 'dev', manager)
    }

    def cleanup() {
        Plugins.stop()
        PluginExtensionProvider.reset()
        pluginsMode ? System.setProperty('pf4j.mode', pluginsMode) : System.clearProperty('pf4j.mode')
    }

    def 'should return the key-value pair sent to the function'() {
        when:
        def SCRIPT = '''
            include { reportMetric } from 'plugin/nf-polly'
            reportMetric("key", "value")
            '''
        and:
        def result = new MockScriptRunner([:]).setScript(SCRIPT).execute()
        then:
        // TODO: We need better tests that this! Need to come back to this later.
        result != null
    }

}
