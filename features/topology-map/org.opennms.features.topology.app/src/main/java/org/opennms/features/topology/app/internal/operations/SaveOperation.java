package org.opennms.features.topology.app.internal.operations;

import java.util.List;

import org.opennms.features.topology.api.Operation;
import org.opennms.features.topology.api.OperationContext;
import org.opennms.features.topology.api.TopologyProvider;
import org.opennms.features.topology.app.internal.topr.SimpleTopologyProvider;


public class SaveOperation implements Operation {
    
    TopologyProvider m_topologyProvider = new SimpleTopologyProvider();
	@Override
    public Undoer execute(List<Object> targets, OperationContext operationContext) {
        m_topologyProvider.save("graph.xml");
        //graphContainer.save("graph.xml");
        return null;
    }

    @Override
    public boolean display(List<Object> targets, OperationContext operationContext) {
        return false;
    }

    @Override
    public boolean enabled(List<Object> targets, OperationContext operationContext) {
        return true;
    }

    @Override
    public String getId() {
        return null;
    }
}