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
package com.alibaba.csp.sentinel.slots.block;

import com.alibaba.csp.sentinel.node.IntervalProperty;

/**
 * @author youji.zj
 * @author jialiang.linjl
 */
public final class RuleConstant {

    /*********阈值类型  0: thread count, 1: QPS *********/
    /**
     * 线程数的意思就是对某个资源正在请求的线程不能超过指定的阈值
     */
    public static final int FLOW_GRADE_THREAD = 0;
    /**
     * 以qps维度统计
     */
    public static final int FLOW_GRADE_QPS = 1;


    /*********熔断 规则 *********/
    public static final int DEGRADE_GRADE_RT = 0;
    /**
     * Degrade by biz exception ratio in the current {@link IntervalProperty#INTERVAL} second(s).
     */
    public static final int DEGRADE_GRADE_EXCEPTION_RATIO = 1;
    /**
     * Degrade by biz exception count in the last 60 seconds.
     */
    public static final int DEGRADE_GRADE_EXCEPTION_COUNT = 2;

    public static final int DEGRADE_DEFAULT_SLOW_REQUEST_AMOUNT = 5;
    public static final int DEGRADE_DEFAULT_MIN_REQUEST_AMOUNT = 5;

    public static final int AUTHORITY_WHITE = 0;
    public static final int AUTHORITY_BLACK = 1;

    /**********流控规则的三种流控模式***********/
    /**
     * 直接的统计维度是当前资源的ClusterNode数据，也就是当前资源在应用中的统计数据
     */
    public static final int STRATEGY_DIRECT = 0;
    /**
     * 关联的统计维度是关联的资源的ClusterNode数据，也就是关联的资源在应用中的统计数据
     */
    public static final int STRATEGY_RELATE = 1;
    /**
     * 链路的统计维度是在指定链路（链路指的也就是context）中的统计数据，也就是DefaultNode的数据
     */
    public static final int STRATEGY_CHAIN = 2;

    /**
     * 快速失败
     */
    public static final int CONTROL_BEHAVIOR_DEFAULT = 0;
    /**
     * 预热
     */
    public static final int CONTROL_BEHAVIOR_WARM_UP = 1;
    /**
     * 排队等待
     */
    public static final int CONTROL_BEHAVIOR_RATE_LIMITER = 2;
    public static final int CONTROL_BEHAVIOR_WARM_UP_RATE_LIMITER = 3;

    public static final int DEFAULT_BLOCK_STRATEGY = 0;
    public static final int TRY_AGAIN_BLOCK_STRATEGY = 1;
    public static final int TRY_UNTIL_SUCCESS_BLOCK_STRATEGY = 2;

    public static final int DEFAULT_RESOURCE_TIMEOUT_STRATEGY = 0;
    public static final int RELEASE_RESOURCE_TIMEOUT_STRATEGY = 1;
    public static final int KEEP_RESOURCE_TIMEOUT_STRATEGY = 2;

    public static final String LIMIT_APP_DEFAULT = "default";
    public static final String LIMIT_APP_OTHER = "other";

    public static final int DEFAULT_SAMPLE_COUNT = 2;
    public static final int DEFAULT_WINDOW_INTERVAL_MS = 1000;

    private RuleConstant() {}
}
