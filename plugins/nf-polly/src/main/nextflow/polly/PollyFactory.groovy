package nextflow.polly

import groovy.transform.CompileStatic
import nextflow.Session
import nextflow.trace.TraceObserver
import nextflow.trace.TraceObserverFactory

/**
 * Implements the validation observer factory
 */
@CompileStatic
class PollyFactory implements TraceObserverFactory {

    @Override
    Collection<TraceObserver> create(Session session) {
        final result = new ArrayList()
        result.add( new PollyObserver() )
        return result
    }
}
