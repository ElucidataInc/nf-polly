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
    final private String graphObserverStreamName
    final private String jobId

    PollyConfig(Map map) {
        def config = map ?: Collections.emptyMap()
        metricsStreamName = config.metricsStreamName ?: "NA"
        graphObserverStreamName = config.graphObserverStreamName ?: "NA"
        jobId = config.jobId ?: "NA"
    }

    String getMetricsStreamName() { metricsStreamName }

    String getGraphObserverStreamName() { graphObserverStreamName }
}
