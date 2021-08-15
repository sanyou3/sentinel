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
package com.alibaba.csp.sentinel.slots.block.flow;

import java.util.Collection;

import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.server.EmbeddedClusterTokenServerProvider;
import com.alibaba.csp.sentinel.cluster.client.TokenClientProvider;
import com.alibaba.csp.sentinel.cluster.TokenResultStatus;
import com.alibaba.csp.sentinel.cluster.TokenResult;
import com.alibaba.csp.sentinel.cluster.TokenService;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.csp.sentinel.util.function.Function;

/**
 * Rule checker for flow control rules.
 *
 * @author Eric Zhao
 */
public class FlowRuleChecker {

    /**
     * 根据当前资源的限流规则，判断当前资源是否能通过
     * <p>
     * 整个逻辑是
     * 1.根据资源找到对应的流控规则
     * 2.根据是否集群来走不同的限流的路
     * 3.选择统计的节点，会根据这个节点获取相应的统计数据进行判断流量
     * 1）
     * 4.根据设置的流控效果进行限流
     *
     * @param ruleProvider
     * @param resource
     * @param context
     * @param node
     * @param count
     * @param prioritized
     * @throws BlockException
     */
    public void checkFlow(Function<String, Collection<FlowRule>> ruleProvider, ResourceWrapper resource,
                          Context context, DefaultNode node, int count, boolean prioritized) throws BlockException {
        if (ruleProvider == null || resource == null) {
            return;
        }
        Collection<FlowRule> rules = ruleProvider.apply(resource.getName());
        if (rules != null) {
            for (FlowRule rule : rules) {
                if (!canPassCheck(rule, context, node, count, prioritized)) {
                    throw new FlowException(rule.getLimitApp(), rule);
                }
            }
        }
    }

    public boolean canPassCheck(/*@NonNull*/ FlowRule rule, Context context, DefaultNode node,
                                             int acquireCount) {
        return canPassCheck(rule, context, node, acquireCount, false);
    }

    public boolean canPassCheck(/*@NonNull*/ FlowRule rule, Context context, DefaultNode node, int acquireCount,
                                             boolean prioritized) {
        String limitApp = rule.getLimitApp();
        if (limitApp == null) {
            //限制的应用不存在时，就能通过
            return true;
        }

        //这里就是新增流控规则是不是集群的选项
        if (rule.isClusterMode()) {
            //是集群
            return passClusterCheck(rule, context, node, acquireCount, prioritized);
        }

        //不是集群
        return passLocalCheck(rule, context, node, acquireCount, prioritized);
    }

    /**
     * 不是集群模式走的逻辑
     * <p>
     * 规律总结：
     * 1） limitApp 仅仅只是对 流控模式选择直接的时候有用，其他一律没用
     * 2） 阈值类型为qps，有三种可供选择的流控效果 ，当为并发线程数时，是快速失败，不可选择
     * </p>
     *
     * @param rule
     * @param context
     * @param node
     * @param acquireCount
     * @param prioritized
     * @return
     */
    private static boolean passLocalCheck(FlowRule rule, Context context, DefaultNode node, int acquireCount,
                                          boolean prioritized) {
        //选择统计节点，所以的限流需要的数据就是根据统计节点来的，所以这个是限流的真正的数据依据
        Node selectedNode = selectNodeByRequesterAndStrategy(rule, context, node);
        if (selectedNode == null) {
            //没找到就通过
            return true;
        }

        //根据流控效果 进行限流，最后返回是不是能通过
        return rule.getRater().canPass(selectedNode, acquireCount, prioritized);
    }

    /**
     * 流控模式选择关联、链路的时候 需要选择关联资源
     *
     * @param rule
     * @param context
     * @param node
     * @return
     */
    static Node selectReferenceNode(FlowRule rule, Context context, DefaultNode node) {
        String refResource = rule.getRefResource();
        int strategy = rule.getStrategy();

        if (StringUtil.isEmpty(refResource)) {
            return null;
        }

        if (strategy == RuleConstant.STRATEGY_RELATE) {
            //关联资源的意思就是当前规则的限流的数据依据来源于关联的资源，而不是自己的
            //就是看别人的脸色，限流不是看自己资源的统计数据，而是别人资源的统计数据
            return ClusterBuilderSlot.getClusterNode(refResource);
        }

        if (strategy == RuleConstant.STRATEGY_CHAIN) {
            //链路的意思就是这个资源是在哪个context上调用的
            // 只有当资源所在的context跟设置的context是一样的时候才会限流。并且统计数据是在该context底下的数据

            if (!refResource.equals(context.getName())) {
                return null;
            }
            return node;
        }
        // No node.
        return null;
    }

    private static boolean filterOrigin(String origin) {
        // Origin cannot be `default` or `other`.
        return !RuleConstant.LIMIT_APP_DEFAULT.equals(origin) && !RuleConstant.LIMIT_APP_OTHER.equals(origin);
    }

    /**
     * 根据配置的规则选择限流数据的统计维度
     * 如果设置了针对某个特定的请求来源（default / other 除外），那么只有当且仅当流控模式选择直接才会限流，否则的话就会根据其他的引用策略来选择选择
     * 设置了默认针对来源（default）
     * <p>
     *            default（所有的来源）	                other（除指定规则的其它来源）	                        某个的特定
     *
     * 直接	 	    当前资源的ClusterNode	            被限制的来源在当前资源数据统计	               被限制的来源在当前资源数据统计
     *
     * 关联	 	   关联资源的ClusterNode	               关联资源的ClusterNode	                            关联资源的ClusterNode
     *
     * 链路	 当前资源在关联的链路（context）中的DefaultNode	当前资源在关联的链路（context）中的DefaultNode	当前资源在关联的链路（context）中的DefaultNode
     *
     * </p>
     * <p>
     * 由上图可知，limitApp 仅仅只是对 流控模式选择直接的时候有用，其他一律没用
     * </p>
     *
     * @param rule
     * @param context
     * @param node
     * @return
     */
    static Node selectNodeByRequesterAndStrategy(/*@NonNull*/ FlowRule rule, Context context, DefaultNode node) {
        // The limit app should not be empty.
        String limitApp = rule.getLimitApp();
        int strategy = rule.getStrategy();
        String origin = context.getOrigin();

        if (limitApp.equals(origin) && filterOrigin(origin)) {
            if (strategy == RuleConstant.STRATEGY_DIRECT) {
                //当限制的资源匹配上了，并且不是默认的资源，并且是直接的流控模式，就返回这个请求的来源在当前资源的统计信息
                // Matches limit origin, return origin statistic node.
                return context.getOriginNode();
            }

            return selectReferenceNode(rule, context, node);
        } else if (RuleConstant.LIMIT_APP_DEFAULT.equals(limitApp)) {
            if (strategy == RuleConstant.STRATEGY_DIRECT) {
                // Return the cluster node.
                return node.getClusterNode();
            }

            return selectReferenceNode(rule, context, node);
        } else if (RuleConstant.LIMIT_APP_OTHER.equals(limitApp)
                && FlowRuleManager.isOtherOrigin(origin, rule.getResource())) {
            if (strategy == RuleConstant.STRATEGY_DIRECT) {
                return context.getOriginNode();
            }

            return selectReferenceNode(rule, context, node);
        }

        return null;
    }

    /**
     * 是集群默认走的逻辑
     *
     * @param rule
     * @param context
     * @param node
     * @param acquireCount
     * @param prioritized
     * @return
     */
    private static boolean passClusterCheck(FlowRule rule, Context context, DefaultNode node, int acquireCount,
                                            boolean prioritized) {
        try {
            TokenService clusterService = pickClusterService();
            if (clusterService == null) {
                return fallbackToLocalOrPass(rule, context, node, acquireCount, prioritized);
            }
            long flowId = rule.getClusterConfig().getFlowId();
            TokenResult result = clusterService.requestToken(flowId, acquireCount, prioritized);
            return applyTokenResult(result, rule, context, node, acquireCount, prioritized);
            // If client is absent, then fallback to local mode.
        } catch (Throwable ex) {
            RecordLog.warn("[FlowRuleChecker] Request cluster token unexpected failed", ex);
        }
        // Fallback to local flow control when token client or server for this rule is not available.
        // If fallback is not enabled, then directly pass.
        return fallbackToLocalOrPass(rule, context, node, acquireCount, prioritized);
    }

    private static boolean fallbackToLocalOrPass(FlowRule rule, Context context, DefaultNode node, int acquireCount,
                                                 boolean prioritized) {
        if (rule.getClusterConfig().isFallbackToLocalWhenFail()) {
            return passLocalCheck(rule, context, node, acquireCount, prioritized);
        } else {
            // The rule won't be activated, just pass.
            return true;
        }
    }

    private static TokenService pickClusterService() {
        if (ClusterStateManager.isClient()) {
            return TokenClientProvider.getClient();
        }
        if (ClusterStateManager.isServer()) {
            return EmbeddedClusterTokenServerProvider.getServer();
        }
        return null;
    }

    private static boolean applyTokenResult(/*@NonNull*/ TokenResult result, FlowRule rule, Context context,
                                                         DefaultNode node,
                                                         int acquireCount, boolean prioritized) {
        switch (result.getStatus()) {
            case TokenResultStatus.OK:
                return true;
            case TokenResultStatus.SHOULD_WAIT:
                // Wait for next tick.
                try {
                    Thread.sleep(result.getWaitInMs());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return true;
            case TokenResultStatus.NO_RULE_EXISTS:
            case TokenResultStatus.BAD_REQUEST:
            case TokenResultStatus.FAIL:
            case TokenResultStatus.TOO_MANY_REQUEST:
                return fallbackToLocalOrPass(rule, context, node, acquireCount, prioritized);
            case TokenResultStatus.BLOCKED:
            default:
                return false;
        }
    }
}