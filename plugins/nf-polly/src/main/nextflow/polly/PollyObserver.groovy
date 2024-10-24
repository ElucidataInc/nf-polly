
package nextflow.polly

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.processor.TaskHandler
import nextflow.processor.TaskProcessor
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
        log.info "-------Pipeline is starting-----------"
        this.session = session
        this.config = new PollyConfig(session.config.navigate('polly') as Map)
        this.env = session.config.navigate('env') as Map
        log.info this.config.toString()
        log.info this.env.toString()
        log.info "_______________________________________"
    }

    @Override
    void onFlowComplete() {
        log.info "----------Pipeline complete-------------"
    }

    /*
     * Invoked when the process is created.
     */
    @Override
    void onProcessCreate(TaskProcessor process ){
        log.info "-------------------Process Created-------------------"
        log.info process.getName()
        putRecordToObserverStream(ProcessStatus.CREATED, process.name)
    }

    /*
      * Invoked when all task have been executed and process ends.
      */
    @Override
    void onProcessTerminate( TaskProcessor process ){
        log.info "-------------------Process Terminated-------------------"
        log.info process.toString()
        putRecordToObserverStream(ProcessStatus.TERMINATED, process.name)
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
        log.info "------Process Pending----------"
        log.info handler.toString()
        log.info trace.toString()
        String task_hash = handler.task.getHash().toString()
        Map params = handler.task.getInputs()
        log.info "__CONFIG__"
        log.info handler.task.getConfig().toMapString()
        log.info params.toMapString()
        log.info task_hash
        putRecordToObserverStream(ProcessStatus.PENDING, handler.task.getName())
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
        log.info "------Process Submitted----------"
        log.info handler.toString()
        log.info trace.toString()
        String task_hash = handler.task.getHash().toString()
        Map params = handler.task.getInputs()
        log.info "__CONFIG__"
        log.info handler.task.getConfig().toMapString()
        log.info params.toMapString()
        log.info task_hash
        putRecordToObserverStream(ProcessStatus.SUBMITTED, handler.task.getName())
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
        log.info "------Process Started----------"
        log.info handler.toString()
        log.info trace.toString()
        putRecordToObserverStream(ProcessStatus.STARTED, handler.task.getName())
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
        log.info "------Process Completed----------"
        log.info handler.toString()
        log.info trace.toString()
        putRecordToObserverStream(ProcessStatus.COMPLETED, handler.task.getName())
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
        log.info "------Process Cached----------"
        log.info handler.toString()
        log.info trace.toString()
        putRecordToObserverStream(ProcessStatus.CACHED, handler.task.getName())

    }

    void putRecordToObserverStream(String status, String processName){
        String streamName = this.config.getGraphObserverStreamName()
        log.info "Stream Name: " + streamName

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
            Map map = [job_id: jobId, status: status, process_name: processName]
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
