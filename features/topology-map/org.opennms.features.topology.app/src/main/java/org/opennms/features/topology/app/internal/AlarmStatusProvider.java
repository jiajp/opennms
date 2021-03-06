package org.opennms.features.topology.app.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opennms.features.topology.api.topo.Criteria;
import org.opennms.features.topology.api.topo.Status;
import org.opennms.features.topology.api.topo.StatusProvider;
import org.opennms.features.topology.api.topo.Vertex;
import org.opennms.features.topology.api.topo.VertexProvider;
import org.opennms.features.topology.api.topo.VertexRef;
import org.opennms.netmgt.dao.api.AlarmDao;
import org.opennms.netmgt.model.OnmsSeverity;
import org.opennms.netmgt.model.alarm.AlarmSummary;
import org.slf4j.LoggerFactory;

public class AlarmStatusProvider implements StatusProvider {

    public static class AlarmStatus implements Status {

        private final String m_label;
        private final long m_alarmCount;

        public AlarmStatus(String label, long count) {
            m_label = label;
            m_alarmCount = count;
        }

        @Override
        public String computeStatus() {
            return m_label.toLowerCase();
        }

        @Override
        public Map<String, String> getStatusProperties() {
            Map<String, String> statusMap = new HashMap<String, String>();
            statusMap.put("status", m_label.toLowerCase());
            statusMap.put("statusCount", "" + m_alarmCount);
            return statusMap;
        }

        @Override
        public String toString() {
            return String.format("[%s: %d]", m_label, m_alarmCount);
        }
    }

    private AlarmDao m_alarmDao;

    public void setAlarmDao(AlarmDao alarmDao) {
        m_alarmDao = alarmDao;
    }

    @Override
    public Map<VertexRef, Status> getStatusForVertices(VertexProvider vertexProvider, Collection<VertexRef> vertices, Criteria[] criteria) {
        Map<VertexRef, Status> returnMap = new HashMap<VertexRef, Status>();

        // split nodes from groups and others
        List<VertexRef> nodeRefs = getNodeVertexRefs(vertexProvider, vertices, criteria); // nodes
        List<VertexRef> otherRefs = getOtherVertexRefs(vertices);  // groups

        Map<Integer, VertexRef> nodeIdMap = extractNodeIds(nodeRefs);
        Map<Integer, AlarmSummary> nodeIdToAlarmSummaryMap = getAlarmSummaries(nodeIdMap.keySet()); // calculate status for ALL nodes

        // status for all known node ids
        for (Integer eachNodeId : nodeIdMap.keySet()) {
            AlarmSummary summary = nodeIdToAlarmSummaryMap.get(eachNodeId);
            AlarmStatus status = summary == null ? createIndeterminateStatus() : createStatus(summary);
            VertexRef ref = nodeIdMap.get(eachNodeId);
            returnMap.put(ref, status);

            LoggerFactory.getLogger(getClass()).debug("Status for node '{}' with id '{}' is: {}", ref.getLabel(), ref.getId(), status);
        }

        // calculate status for groups and nodes which are neither group nor node
        for (VertexRef eachRef : otherRefs) {
            if (isGroup(eachRef)) {
                List<AlarmSummary> alarmSummariesForGroup = new ArrayList<AlarmSummary>();
                List<Vertex> children = vertexProvider.getChildren(eachRef, criteria);
                for (Vertex eachChildren : children) {
                    AlarmSummary eachChildrenAlarmSummary = nodeIdToAlarmSummaryMap.get(eachChildren.getNodeID());
                    if (eachChildrenAlarmSummary != null) {
                        alarmSummariesForGroup.add(eachChildrenAlarmSummary);
                    }
                }

                AlarmStatus groupStatus = calculateAlarmStatusForGroup(alarmSummariesForGroup);
                returnMap.put(eachRef, groupStatus);
            } else {
                returnMap.put(eachRef, createIndeterminateStatus());
            }
        }

        return returnMap;
    }

    private Map<Integer , AlarmSummary> getAlarmSummaries(Set<Integer> nodeIds) {
        Map<Integer, AlarmSummary> resultMap = new HashMap<Integer, AlarmSummary>();

        List<AlarmSummary> alarmSummaries = m_alarmDao.getNodeAlarmSummariesIncludeAcknowledgedOnes(new ArrayList<Integer>(nodeIds));
        for (AlarmSummary eachSummary : alarmSummaries) {
            resultMap.put(eachSummary.getNodeId(), eachSummary);
        }

        return resultMap;
    }

    private static AlarmStatus createStatus(AlarmSummary summary) {
        AlarmStatus status = new AlarmStatus(summary.getMaxSeverity().getLabel(), summary.getAlarmCount());
        return status;
    }

    private static Map<Integer, VertexRef> extractNodeIds(Collection<VertexRef> inputList) {
        Map<Integer, VertexRef> vertexRefToNodeIdMap = new HashMap<Integer, VertexRef>();

        for (VertexRef eachRef : inputList) {
            if ("nodes".equals(eachRef.getNamespace())) {
                try {
                    Integer nodeId = Integer.parseInt(eachRef.getId());
                    if (nodeId != null) {
                        vertexRefToNodeIdMap.put(nodeId, eachRef);
                    }
                } catch (NumberFormatException nfe) {
                    LoggerFactory.getLogger(AlarmStatusProvider.class).warn("Could not parse id '{}' of vertex '{}' as integer.", eachRef.getId(), eachRef);
                }
            }
        }

        return vertexRefToNodeIdMap;
    }

    private static List<VertexRef> getNodeVertexRefs(VertexProvider vertexProvider, Collection<VertexRef> vertices, Criteria[] criteria) {
        List<VertexRef> returnList = new ArrayList<VertexRef>();
         for (VertexRef eachRef : vertices) {
            if ("nodes".equals(eachRef.getNamespace())) {
                if(isGroup(eachRef)) {
                    addChildrenRecursively(vertexProvider, eachRef, returnList, criteria);
                } else {
                    if (!returnList.contains(eachRef)) {
                        returnList.add(eachRef);
                    }
                }
            }
         }
        return returnList;
    }

    private static List<VertexRef> getOtherVertexRefs(Collection<VertexRef> vertices) {
        List<VertexRef> returnList = new ArrayList<VertexRef>();
        for (VertexRef eachRef : vertices) {
            if (!"nodes".equals(eachRef.getNamespace())) {
                returnList.add(eachRef); // we do not need to check for groups, because a group would have a namespace "nodes"
            }
        }
        return returnList;
    }

    private static void addChildrenRecursively(VertexProvider vertexProvider, VertexRef groupRef, Collection<VertexRef> vertexRefs, Criteria[] criteria) {
        List<Vertex> vertices = vertexProvider.getChildren(groupRef, criteria);
        for(Vertex vertex : vertices) {
            if(!vertex.isGroup()) {
                if (!vertexRefs.contains(vertex)) {
                    vertexRefs.add(vertex);
                }
            } else {
                addChildrenRecursively(vertexProvider, vertex, vertexRefs, criteria);
            }
        }
    }

    private static boolean isGroup(VertexRef vertexRef) {
        if(vertexRef instanceof Vertex) {
            return ((Vertex) vertexRef).isGroup();
        }
        return false;
    }

    private static AlarmStatus createIndeterminateStatus() {
        return new AlarmStatus(OnmsSeverity.INDETERMINATE.getLabel(), 0);
    }

    private static AlarmStatus calculateAlarmStatusForGroup(List<AlarmSummary> alarmSummaries) {
        if (!alarmSummaries.isEmpty()) {
            Collections.sort(alarmSummaries, new Comparator<AlarmSummary>() {
                @Override
                public int compare(AlarmSummary o1, AlarmSummary o2) {
                    return o1.getMaxSeverity().compareTo(o2.getMaxSeverity());
                }
            });
            OnmsSeverity severity = alarmSummaries.get(0).getMaxSeverity();
            int count = 0;
            for (AlarmSummary eachSummary : alarmSummaries) {
                count += eachSummary.getAlarmCount();
            }
            return new AlarmStatus(severity.getLabel(), count);
        }
        return createIndeterminateStatus();
    }
}
