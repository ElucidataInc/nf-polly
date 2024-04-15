package nextflow.polly


import groovy.json.JsonOutput
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

    /**
     * A map of 'env' variables set in the Nextflow config file
     */
    private Map env

    /*
     * nf-core initializes the plugin once loaded and session is ready
     * @param session
     */

    @Override
    protected void init(Session session) {
        this.session = session
        this.config = new PollyConfig(session.config.navigate('polly') as Map)
        this.env = session.config.navigate('env') as Map
    }

    /**
     * Report a single metric. This metric will be attached to the currently running job's ID. It
     * can help provide domain-specific intelligence to pipeline job metrics.
     * @param key The name of the metric
     * @param value The value of the metric. Must be either a boolean, a number (int/float) or a
     * string.
     */
    @Function
    void reportMetric(var key, var value) {
        logger.info(String.format("Putting record with key='%s' & value='%s'", key, value))

        String streamName = this.config.getMetricsStreamName()
        if (streamName == "NA") {
            logger.error("No stream set for process to send metrics to. Unable to report metric.")
            return
        }

        String jobId = this.env.get("JOB_ID") ?: "NA"
        if (jobId == "NA") {
            logger.error("No JOB_ID set for process. Unable to report metric.")
            return
        }

        String partitionKey = key.toString()
        try {
            Map map = [job_id: jobId, key: key, value: value, type: getValueType(value)]
            byte[] json = JsonOutput.toJson(map).getBytes()
            KinesisClient client = KinesisClient.builder().build()
            PutRecordRequest putRequest = PutRecordRequest.builder()
                    .partitionKey(partitionKey)
                    .streamName(streamName)
                    .data(SdkBytes.fromByteArray(json))
                    .build() as PutRecordRequest
            PutRecordResponse response = client.putRecord(putRequest)
            logger.info(
                    String.format(
                            "Submitted record %s to stream shard %s",
                            response.sequenceNumber(),
                            response.shardId()
                    )
            )
        } catch (Exception e) {
            logger.error("Failed to produce: " + e.getMessage())
        }
    }

    private static String getValueType(var value) {
        if (value instanceof Boolean) {
            return "boolean"
        }
        if (value instanceof Number) {
            return "number"
        }
        return "string"
    }
}
