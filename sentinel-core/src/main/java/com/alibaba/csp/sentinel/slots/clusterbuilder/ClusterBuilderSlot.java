/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.slots.clusterbuilder;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.csp.sentinel.Constants;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.node.ClusterNode;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.node.IntervalProperty;
import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.node.SampleCountProperty;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlotChain;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slotchain.StringResourceWrapper;
import com.alibaba.csp.sentinel.spi.Spi;

/**
 * <p>
 * This slot maintains resource running statistics (response time, qps, thread
 * count, exception), and a list of callers as well which is marked by
 * {@link ContextUtil#enter(String origin)}
 * </p>
 * <p>
 * One resource has only one cluster node, while one resource can have multiple
 * default nodes.
 * </p>
 * <>
 *     主要有干了两件事
 *     1) 创建或者获取ClusterNode，只创建一次，后面都是获取，同一个资源在同一台机器上只有一个ClusterNode
 *     2) 创建或者获取调用源的统计数据StatisticNode，也就是上游服务的关于当当前资源的调用信息
 * </>
 *
 * @author jialiang.linjl
 */
@Spi(isSingleton = false, order = Constants.ORDER_CLUSTER_BUILDER_SLOT)
public class ClusterBuilderSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    /**
     * <p>
     * Remember that same resource({@link ResourceWrapper#equals(Object)}) will share
     * the same {@link ProcessorSlotChain} globally, no matter in which context. So if
     * code goes into {@link #entry(Context, ResourceWrapper, DefaultNode, int, boolean, Object...)},
     * the resource name must be same but context name may not.
     * </p>
     * <p>
     * To get total statistics of the same resource in different context, same resource
     * shares the same {@link ClusterNode} globally. All {@link ClusterNode}s are cached
     * in this map.
     * </p>
     * <p>
     * The longer the application runs, the more stable this mapping will
     * become. so we don't concurrent map but a lock. as this lock only happens
     * at the very beginning while concurrent map will hold the lock all the time.
     * </p>
     *
     * <p>
     * 整体的意思分为三点：
     * 1）相同的资源是可能在不同的context的，所以进入entry方法的可能是同一个资源但是来自不同的context
     * 2）为了统计资源在不同的context的统计信息，所以所有的资源共享一个 {@link ClusterNode} 一个资源在一台机器中只有一个 ClusterNode
     * 3）为什么不用 concurrent map 存 ClusterNode ，因为当项目越走到后面 clusterNodeMap 是趋于一个固定的值，那么其实只有前期会慢，后期会快
     * </P>
     */
    private static volatile Map<ResourceWrapper, ClusterNode> clusterNodeMap = new HashMap<>();

    private static final Object lock = new Object();

    /**
     * 当前这个资源在所有的context中的统计信息的总和
     */
    private volatile ClusterNode clusterNode = null;

    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, DefaultNode node, int count,
                      boolean prioritized, Object... args)
        throws Throwable {
        if (clusterNode == null) {
            synchronized (lock) {
                if (clusterNode == null) {
                    // Create the cluster node.
                    clusterNode = new ClusterNode(resourceWrapper.getName(), resourceWrapper.getResourceType());
                    HashMap<ResourceWrapper, ClusterNode> newMap = new HashMap<>(Math.max(clusterNodeMap.size(), 16));
                    newMap.putAll(clusterNodeMap);
                    newMap.put(node.getId(), clusterNode);

                    clusterNodeMap = newMap;
                }
            }
        }

        //设置当前资源在整个应用中被访问的数据统计
        node.setClusterNode(clusterNode);

        /*
         * if context origin is set, we should get or create a new {@link Node} of
         * the specific origin.
         */
        if (!"".equals(context.getOrigin())) {
            //构建调用源节点，某个调用源，就是某个应用，服务对当前资源访问的统计信息
            Node originNode = node.getClusterNode().getOrCreateOriginNode(context.getOrigin());
            context.getCurEntry().setOriginNode(originNode);
        }

        fireEntry(context, resourceWrapper, node, count, prioritized, args);
    }

    @Override
    public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        fireExit(context, resourceWrapper, count, args);
    }

    /**
     * Get {@link ClusterNode} of the resource of the specific type.
     *
     * @param id   resource name.
     * @param type invoke type.
     * @return the {@link ClusterNode}
     */
    public static ClusterNode getClusterNode(String id, EntryType type) {
        return clusterNodeMap.get(new StringResourceWrapper(id, type));
    }

    /**
     * Get {@link ClusterNode} of the resource name.
     *
     * @param id resource name.
     * @return the {@link ClusterNode}.
     */
    public static ClusterNode getClusterNode(String id) {
        if (id == null) {
            return null;
        }
        ClusterNode clusterNode = null;

        for (EntryType nodeType : EntryType.values()) {
            clusterNode = clusterNodeMap.get(new StringResourceWrapper(id, nodeType));
            if (clusterNode != null) {
                break;
            }
        }

        return clusterNode;
    }

    /**
     * Get {@link ClusterNode}s map, this map holds all {@link ClusterNode}s, it's key is resource name,
     * value is the related {@link ClusterNode}. <br/>
     * DO NOT MODIFY the map returned.
     *
     * @return all {@link ClusterNode}s
     */
    public static Map<ResourceWrapper, ClusterNode> getClusterNodeMap() {
        return clusterNodeMap;
    }

    /**
     * Reset all {@link ClusterNode}s. Reset is needed when {@link IntervalProperty#INTERVAL} or
     * {@link SampleCountProperty#SAMPLE_COUNT} is changed.
     */
    public static void resetClusterNodes() {
        for (ClusterNode node : clusterNodeMap.values()) {
            node.reset();
        }
    }
}
