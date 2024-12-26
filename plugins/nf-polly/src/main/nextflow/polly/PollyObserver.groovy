
package nextflow.polly

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.processor.TaskHandler
import nextflow.processor.TaskProcessor
import nextflow.script.params.InParam
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kinesis.KinesisClient
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse


class ProcessStatus {
    public static String CREATED = "created"
    public static String SUBMITTED = "submitted"
    public static String PENDING = "pending"
    public static String STARTED = "started"
    public static String CACHED = "cached"
    public static String TERMINATED = "terminated"
    public static String COMPLETED = "completed"
}


@Slf4j
@CompileStatic
class PollyObserver implements TraceObserver {

    static final Logger logger = LoggerFactory.getLogger(PollyExtension.class)
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
     *    graphObserverStreamName = "pravaah-dev-graph-observer-stream-v1"
     * }
     */
    private PollyConfig config

    /**
     * A map of 'env' variables set in the Nextflow config file
     */
    private Map env


    @Override
    void onFlowCreate(Session session) {
        this.session = session
        this.config = new PollyConfig(session.config.navigate('polly') as Map)
        this.env = session.config.navigate('env') as Map
    }

    @Override
    void onFlowComplete() {
        log.info "----------Pipeline complete-------------"
    }

    /**
     * This method when a new task is created and submitted in the nextflow
     * internal queue of pending task to be scheduled to the underlying
     * execution backend
     *
     * @param handler
     * @param trace
     */
    @Override
    void onProcessPending(TaskHandler handler, TraceRecord trace){
        log.info "Process Pending: " + handler.task.getName()
        Map<String, Object> data = getDataFromHandlerAndTrace(handler, trace)
        putRecordToObserverStream(ProcessStatus.PENDING, handler.task.getName(), data)
    }

    /**
     * This method is invoked before a process run is going to be submitted
     *
     * @param handler
     *      The {@link TaskHandler} instance for the current task.
     * @param trace
     *      The associated {@link TraceRecord} for the current task.
     */
    @Override
    void onProcessSubmit(TaskHandler handler, TraceRecord trace){
        Map<String, Object> data = getDataFromHandlerAndTrace(handler, trace)
        log.info "Submitting the process: " + handler.task.getName() + " hash: " + handler.task.getHash().toString()
        putRecordToObserverStream(ProcessStatus.SUBMITTED, handler.task.getName(), data)
    }

    /**
     * This method is invoked when a process run is going to start
     *
     * @param handler
     *      The {@link TaskHandler} instance for the current task.
     * @param trace
     *      The associated {@link TraceRecord} for the current task.
     */
    @Override
    void onProcessStart(TaskHandler handler, TraceRecord trace){
        log.info "Process Started: " + handler.task.getName()
        Map<String, Object> data = getDataFromHandlerAndTrace(handler, trace)
        putRecordToObserverStream(ProcessStatus.STARTED, handler.task.getName(), data)
    }

    /**
     * This method is invoked when a process run completes
     *
     * @param handler
     *      The {@link TaskHandler} instance for the current task.
     * @param trace
     *      The associated {@link TraceRecord} for the current task.
     */
    @Override
    void onProcessComplete(TaskHandler handler, TraceRecord trace){
        log.info "Process Completed: " + handler.task.getName()
        Map<String, Object> data = getDataFromHandlerAndTrace(handler, trace)
        putRecordToObserverStream(ProcessStatus.COMPLETED, handler.task.getName(), data)
    }

    /**
     * method invoked when a task execution is skipped because the result is cached (already computed)
     * or stored (due to the usage of `storeDir` directive)
     *
     * @param handler
     *      The {@link TaskHandler} instance for the current task
     * @param trace
     *      The trace record for the cached trace. When this event is invoked for a store task
     *      the {@code trace} record is expected to be {@code null}
     */
    @Override
    void onProcessCached(TaskHandler handler, TraceRecord trace){
        Map<String, Object> data = getDataFromHandlerAndTrace(handler, trace)
        putRecordToObserverStream(ProcessStatus.CACHED, handler.task.getName(), data)
    }


    Map<String, Object> getDataFromHandlerAndTrace(TaskHandler handler, TraceRecord trace){
        Map<String, Object> data = [:]
        Map<String, Object> input_map = [:]
        def inputs = handler.task.getInputs()
        for ( input in inputs ) {
            InParam param = input.key
            input_map[param.getName()] = input.value.getClass().getName()
        }

        String infra = this.env.get("INFRA") ?: "NA"

        data['infra'] = infra
        data['process_hash'] = handler.task.getHash().toString()
        data['machine_config'] = [
                'native_id': trace.getProperty('native_id') ?: 'null',
                'cpus': trace.getProperty('cpus') ?: 'null',
                'memory': trace.getProperty('memory') ?: 'null',
                'disk': trace.getProperty('disk') ?: 'null'
        ]
        return data
    }


    void putRecordToObserverStream(String status, String processName, Map<String, Object> data){
        String streamName = this.config.getGraphObserverStreamName()
        log.info "Stream Name: " + streamName

        if (streamName == "NA"){
            streamName =  this.env.get("GRAPH_OBSERVER_STREAM_NAME") ?: "NA"
        }

        if (streamName == "NA") {
            logger.error("No stream set for process to send metrics to. Unable to report metric.")
            return
        }

        String jobId = this.env.get("RUN_ID") ?: "NA"
        if (jobId == "NA") {
            logger.error("No JOB_ID set for process. Unable to report metric.")
            return
        }

        String partitionKey = status.toString()
        try {
            Map map = [job_id: jobId, status: status, process_name: processName, task_detail: data]
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

}
