package nextflow.polly

import groovy.transform.PackageScope


/**
 * This class allows model an specific configuration, extracting values from a map and converting
 *
 * We anotate this class as @PackageScope to restrict the access of their methods only to class in
 * the same package
 */
@PackageScope
class PollyConfig {

    final private String metricsStreamName

    PollyConfig(Map map) {
        def config = map ?: Collections.emptyMap()
        metricsStreamName = config.metricsStreamName ?: 'pravaah-dev-user-defined-metrics-events-v1'
    }

    String getMetricsStreamName() { metricsStreamName }
}
