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

import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slots.block.flow.controller.DefaultController;

/**
 * A universal interface for traffic shaping controller.
 * <p>
 * 流控规则中最终决定有没有被限流，当有请求过来时，由该接口来决定当前资源能不能通过，也就是有没有被限流，
 * </p>
 * 流控效果接口
 * 对于qps限流来说有有三种效果：快速失败 、 warm up 、排队等待
 * 对于线程数来说页面是没有选项的，默认就是快速失败
 * {@link DefaultController} 快速失败
 *
 * @author jialiang.linjl
 */
public interface TrafficShapingController {

    /**
     * Check whether given resource entry can pass with provided count.
     *
     * @param node         resource node
     * @param acquireCount count to acquire
     * @param prioritized  whether the request is prioritized
     * @return true if the resource entry can pass; false if it should be blocked
     */
    boolean canPass(Node node, int acquireCount, boolean prioritized);

    /**
     * Check whether given resource entry can pass with provided count.
     *
     * @param node         resource node
     * @param acquireCount count to acquire
     * @return true if the resource entry can pass; false if it should be blocked
     */
    boolean canPass(Node node, int acquireCount);
}
