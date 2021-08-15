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

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.system.SystemRule;

/**
 * Base interface of all rules.
 * 这个类就是对页面数据响应规则数据的封装
 * <p>
 * 当页面设置的数据到达后，根据规则的类型对数据进行处理
 * {@link FlowRule} 流控规则
 * {@link DegradeRule} 熔断规则
 * {@link SystemRule} 系统规则
 *
 * @author youji.zj
 */
public interface Rule {

    /**
     * Get target resource of this rule.
     *
     * @return target resource of this rule
     */
    String getResource();

}
