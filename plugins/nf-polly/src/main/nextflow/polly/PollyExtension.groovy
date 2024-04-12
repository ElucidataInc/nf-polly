package nextflow.polly

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.plugin.extension.Function
import nextflow.plugin.extension.PluginExtensionPoint
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kinesis.KinesisClient
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse

/**
 * Example plugin extension showing how to implement a basic
 * channel factory method, a channel operator and a custom function.
 */
@Slf4j
@CompileStatic
class PollyExtension extends PluginExtensionPoint {

    static final Logger logger = LoggerFactory.getLogger(PollyExtension.class)

    // static final ObjectMapper objMapper = new ObjectMapper();
    /*
     * A session hold information about current execution of the script
     */
    private Session session

    /*
     * A Custom config extracted from nextflow.config under polly tag
     * nextflow.config
     * ---------------
     * docker {
     *   enabled = true
     * }
     * ...
     * polly {
     *    metricsStreamName = "my-kinesis-stream"
     * }
     */
    private PollyConfig config

    /*
     * nf-core initializes the plugin once loaded and session is ready
     * @param session
     */

    @Override
    protected void init(Session session) {
        this.session = session
        this.config = new PollyConfig(session.config.navigate('polly') as Map)
    }

    @Function
    void reportMetric(var key, var value) {
    // logger.info("Starting PutRecord Producer");
    String streamName = "pravaah-dev-user-defined-metrics-events-v1";
    String partitionKey = "12345";
    Map<Object, Object> keyValuePairs = Map.of(key, value);
    try {
        byte[] data = new ObjectMapper().writeValueAsBytes(Map.of("metricfromTest5", keyValuePairs));
        KinesisClient client = KinesisClient.builder().build();
        PutRecordRequest putRequest = PutRecordRequest.builder()
                .partitionKey(partitionKey)
                .streamName(streamName)
                .data(SdkBytes.fromByteArray(data))
                .build();
        PutRecordResponse response = client.putRecord(putRequest);
        System.out.println("Produced Record " + response.sequenceNumber() + " to Shard " + response.shardId() + " (line 145)");
    } catch (Exception e) {
        System.out.println("Failed to produce: " + e.getMessage());
    }
}
}
